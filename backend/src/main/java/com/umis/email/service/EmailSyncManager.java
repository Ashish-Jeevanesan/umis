package com.umis.email.service;

import com.umis.common.exception.ResourceNotFoundException;
import com.umis.email.model.IngestionJob;
import com.umis.email.model.TriggerType;
import com.umis.email.repository.IngestionJobRepository;
import com.umis.mailbox.connector.MailboxConnector;
import com.umis.mailbox.connector.MailboxConnectorRegistry;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxStatus;
import com.umis.mailbox.model.MailboxSyncState;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.mailbox.repository.MailboxCredentialsRepository;
import com.umis.mailbox.repository.MailboxSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager orchestrating mailbox synchronization, concurrency locks, and scheduled polling runs.
 */
@Service
public class EmailSyncManager {

    private static final Logger log = LoggerFactory.getLogger(EmailSyncManager.class);

    private final MailboxConnectionRepository connectionRepository;
    private final MailboxCredentialsRepository credentialsRepository;
    private final MailboxSyncStateRepository syncStateRepository;
    private final MailboxConnectorRegistry connectorRegistry;
    private final EmailIngestionService ingestionService;
    private final IngestionJobRepository jobRepository;

    // Self-injected proxy reference. Spring's @Transactional (and @Async) advice is applied via
    // a dynamic proxy that only intercepts calls coming in from OTHER beans - a method on this
    // same class calling synchronizeMailbox(...) directly (self-invocation) bypasses that proxy
    // entirely, silently running it with no transaction. That's what broke both scheduled polling
    // and webhook-triggered sync: acquireSyncLock()'s @Modifying query then failed with
    // "TransactionRequiredException: Executing an update/delete query requires a transaction".
    // Calling through `self` instead routes back through the real proxy. @Lazy avoids a circular
    // construction error (Spring resolves it once the proxy already exists).
    private final EmailSyncManager self;

    public EmailSyncManager(
            MailboxConnectionRepository connectionRepository,
            MailboxCredentialsRepository credentialsRepository,
            MailboxSyncStateRepository syncStateRepository,
            MailboxConnectorRegistry connectorRegistry,
            EmailIngestionService ingestionService,
            IngestionJobRepository jobRepository,
            @Lazy EmailSyncManager self
    ) {
        this.connectionRepository = connectionRepository;
        this.credentialsRepository = credentialsRepository;
        this.syncStateRepository = syncStateRepository;
        this.connectorRegistry = connectorRegistry;
        this.ingestionService = ingestionService;
        this.jobRepository = jobRepository;
        this.self = self;
    }

    @Async
    public CompletableFuture<IngestionJob> synchronizeMailboxAsync(UUID mailboxId, TriggerType triggerType) {
        return CompletableFuture.completedFuture(self.synchronizeMailbox(mailboxId, triggerType));
    }

    @Transactional
    public IngestionJob synchronizeMailbox(UUID mailboxId, TriggerType triggerType) {
        MailboxConnection mailbox = connectionRepository.findById(mailboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox not found: " + mailboxId));

        if (mailbox.getStatus() == MailboxStatus.DISCONNECTED || mailbox.getStatus() == MailboxStatus.SUSPENDED) {
            log.info("Skipping sync for mailbox {} due to status: {}", mailboxId, mailbox.getStatus());
            return null;
        }

        // Ensure sync state record exists
        MailboxSyncState syncState = syncStateRepository.findByMailboxId(mailboxId)
                .orElseGet(() -> syncStateRepository.save(new MailboxSyncState(mailbox)));

        String lockToken = acquireSyncLock(mailboxId);
        if (lockToken == null) {
            log.warn("Synchronization lock already held for mailbox: {}", mailboxId);
            return null;
        }

        IngestionJob job = jobRepository.save(new IngestionJob(mailbox.getTenantId(), mailbox, triggerType));
        com.umis.security.TenantContext.setTenantId(mailbox.getTenantId());

        try {
            executeSync(mailbox, syncState, job);
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            mailbox.setLastSuccessfulSyncAt(Instant.now());
            mailbox.setStatus(MailboxStatus.ACTIVE);
            mailbox.setErrorMessage(null);
        } catch (Exception exception) {
            log.error("Sync error for mailbox {}: {}", mailboxId, exception.getMessage(), exception);
            job.setStatus("FAILED");
            job.setErrorSummary(exception.getMessage());
            job.setCompletedAt(Instant.now());
            mailbox.setStatus(MailboxStatus.CONNECTION_ERROR);
            mailbox.setFailureCount(mailbox.getFailureCount() + 1);
            mailbox.setErrorMessage(exception.getMessage());
        } finally {
            com.umis.security.TenantContext.clear();
            mailbox.setLastSyncAt(Instant.now());
            connectionRepository.save(mailbox);
            releaseSyncLock(mailboxId, lockToken);
            jobRepository.save(job);
        }

        return job;
    }

    private void executeSync(MailboxConnection mailbox, MailboxSyncState syncState, IngestionJob job) {
        MailboxCredentials credentials = credentialsRepository.findByMailboxId(mailbox.getId())
                .orElseThrow(() -> new IllegalStateException("Credentials missing for mailbox: " + mailbox.getId()));

        MailboxConnector connector = connectorRegistry.getConnector(mailbox.getProvider());
        refreshCredentialsIfExpired(connector, credentials);

        FetchResult fetchResult = fetchWithCursorRecovery(connector, mailbox, credentials, syncState);

        job.setItemsDiscovered(fetchResult.messages().size());

        for (NormalizedEmail normalized : fetchResult.messages()) {
            try {
                EmailIngestionService.IngestionResult result =
                        ingestionService.processNormalizedEmail(mailbox, normalized);
                if (result.isDuplicate()) {
                    job.setItemsSkipped(job.getItemsSkipped() + 1);
                } else {
                    job.setItemsIngested(job.getItemsIngested() + 1);
                }
            } catch (Exception itemException) {
                log.error("Failed to ingest message {}: {}",
                        normalized.providerMessageId(), itemException.getMessage());
                job.setItemsFailed(job.getItemsFailed() + 1);
            }
        }

        if (fetchResult.newCursor() != null) {
            syncState.setCursorValue(fetchResult.newCursor());
        }
        if (fetchResult.lastHistoryId() != null) {
            syncState.setLastHistoryId(fetchResult.lastHistoryId());
        }
        if (fetchResult.lastUid() != null) {
            syncState.setLastUid(fetchResult.lastUid());
        }
        syncStateRepository.save(syncState);
    }

    private FetchResult fetchWithCursorRecovery(
            MailboxConnector connector,
            MailboxConnection mailbox,
            MailboxCredentials credentials,
            MailboxSyncState syncState
    ) {
        try {
            return connector.fetchMessages(mailbox, credentials, syncState.getCursorValue(), 50);
        } catch (Exception fetchException) {
            String strError = fetchException.getMessage() != null ? fetchException.getMessage().toLowerCase() : "";
            if ((strError.contains("401") || strError.contains("unauthorized") || strError.contains("invalid credentials"))
                    && credentials.getEncryptedRefreshToken() != null) {
                log.info("Token expired during sync for mailbox {}. Refreshing credentials...", mailbox.getId());
                if (connector.refreshCredentials(credentials)) {
                    credentialsRepository.save(credentials);
                    return connector.fetchMessages(mailbox, credentials, syncState.getCursorValue(), 50);
                }
            }
            if (strError.contains("resyncrequired") || strError.contains("expired")
                    || strError.contains("410") || strError.contains("cursor invalid")) {
                log.warn("Cursor expired for mailbox {}. Resetting cursor for fresh sync.", mailbox.getId());
                syncState.setCursorValue(null);
                syncState.setLastHistoryId(null);
                syncStateRepository.save(syncState);
                return connector.fetchMessages(mailbox, credentials, null, 50);
            }
            throw fetchException;
        }
    }

    private void refreshCredentialsIfExpired(MailboxConnector connector, MailboxCredentials credentials) {
        Instant expiry = credentials.getTokenExpiresAt();
        if (expiry != null && expiry.isBefore(Instant.now().plusSeconds(60))) {
            if (connector.refreshCredentials(credentials)) {
                credentialsRepository.save(credentials);
            }
        }
    }

    private String acquireSyncLock(UUID mailboxId) {
        Instant now = Instant.now();
        Instant expireBefore = now.minus(15, ChronoUnit.MINUTES);
        String lockToken = UUID.randomUUID().toString();
        int updatedRows = syncStateRepository.acquireLock(mailboxId, lockToken, now, expireBefore);
        return updatedRows > 0 ? lockToken : null;
    }

    private void releaseSyncLock(UUID mailboxId, String lockToken) {
        if (lockToken != null) {
            syncStateRepository.releaseLock(mailboxId, lockToken);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduledPollingRunner() {
        List<MailboxConnection> activeMailboxes = connectionRepository.findAllByStatus(MailboxStatus.ACTIVE);
        Instant now = Instant.now();

        // Round-robin / fair scheduling: sort by oldest sync time first
        activeMailboxes.sort((mbox1, mbox2) -> {
            if (mbox1.getLastSyncAt() == null) return -1;
            if (mbox2.getLastSyncAt() == null) return 1;
            return mbox1.getLastSyncAt().compareTo(mbox2.getLastSyncAt());
        });

        for (MailboxConnection mailbox : activeMailboxes) {
            Instant lastSync = mailbox.getLastSyncAt();
            long intervalMinutes = mailbox.getPollingIntervalMinutes() > 0 ? mailbox.getPollingIntervalMinutes() : 5;

            if (lastSync == null || lastSync.isBefore(now.minus(intervalMinutes, ChronoUnit.MINUTES))) {
                try {
                    self.synchronizeMailbox(mailbox.getId(), TriggerType.POLLING);
                } catch (Exception exception) {
                    log.warn("Scheduled polling failed for mailbox {}: {}", mailbox.getId(), exception.getMessage());
                }
            }
        }
    }
}
