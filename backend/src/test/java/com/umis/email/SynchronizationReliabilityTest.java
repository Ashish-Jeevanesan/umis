package com.umis.email;

import com.umis.common.exception.MailboxIntegrationException;
import com.umis.email.model.IngestionJob;
import com.umis.email.model.TriggerType;
import com.umis.email.service.EmailSyncManager;
import com.umis.mailbox.connector.MailboxConnector;
import com.umis.mailbox.connector.MailboxConnectorRegistry;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.MailboxSyncState;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.mailbox.repository.MailboxCredentialsRepository;
import com.umis.mailbox.repository.MailboxSyncStateRepository;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SynchronizationReliabilityTest {

    @Autowired
    private EmailSyncManager syncManager;

    @Autowired
    private MailboxConnectionRepository connectionRepository;

    @Autowired
    private MailboxCredentialsRepository credentialsRepository;

    @Autowired
    private MailboxSyncStateRepository syncStateRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockBean
    private MailboxConnectorRegistry connectorRegistry;

    private MailboxConnector mockConnector = org.mockito.Mockito.mock(MailboxConnector.class);

    private Tenant tenant;
    private MailboxConnection mailbox;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("ORG-SYNC", "Sync Org"));
        mailbox = connectionRepository.save(new MailboxConnection(
                tenant.getId(), "sync@org-sync.com", "Sync Mailbox",
                MailboxProvider.MICROSOFT_365, SyncStrategy.POLLING));

        MailboxCredentials creds = new MailboxCredentials(mailbox, "OAUTH2");
        creds.setEncryptedAccessToken("dummy_token");
        credentialsRepository.save(creds);

        when(connectorRegistry.getConnector(any())).thenReturn(mockConnector);
    }

    @Test
    @DisplayName("Two workers attempting to synchronize the same mailbox: second worker is blocked by lock")
    void testMutualExclusionDuringSync() {
        // Pre-acquire lock to simulate Worker 1 actively running
        MailboxSyncState state = syncStateRepository.findByMailboxId(mailbox.getId())
                .orElseGet(() -> syncStateRepository.save(new MailboxSyncState(mailbox)));
        state.setSyncLockToken("worker_1_lock_token");
        state.setSyncLockAcquiredAt(Instant.now());
        syncStateRepository.save(state);

        // Worker 2 attempts synchronization -> must be skipped
        IngestionJob job = syncManager.synchronizeMailbox(mailbox.getId(), TriggerType.MANUAL_SYNC);
        assertNull(job, "Worker 2 must receive null because lock is actively held by Worker 1");
    }

    @Test
    @DisplayName("Expired lock is overridden after 15 minutes of inactivity")
    void testExpiredLockRecovery() {
        // Pre-set an expired lock token (>15 minutes old)
        MailboxSyncState state = syncStateRepository.findByMailboxId(mailbox.getId())
                .orElseGet(() -> syncStateRepository.save(new MailboxSyncState(mailbox)));
        state.setSyncLockToken("dead_worker_token");
        state.setSyncLockAcquiredAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        syncStateRepository.save(state);

        when(mockConnector.fetchMessages(any(), any(), any(), anyInt()))
                .thenReturn(FetchResult.of(List.of(), "cursor_new", false));

        IngestionJob job = syncManager.synchronizeMailbox(mailbox.getId(), TriggerType.POLLING);
        assertNotNull(job, "Lock acquisition must succeed when existing lock has expired");
        assertEquals("COMPLETED", job.getStatus());
    }

    @Test
    @DisplayName("Expired cursor recovery: delta error resets cursor and performs fresh sync")
    void testExpiredCursorRecovery() {
        MailboxSyncState state = syncStateRepository.findByMailboxId(mailbox.getId())
                .orElseGet(() -> syncStateRepository.save(new MailboxSyncState(mailbox)));
        state.setCursorValue("stale_delta_link_123");
        syncStateRepository.save(state);

        // First call with stale cursor throws expired error
        when(mockConnector.fetchMessages(any(), any(), eq("stale_delta_link_123"), anyInt()))
                .thenThrow(new MailboxIntegrationException("Microsoft Graph delta cursor expired: 410 resyncRequired"));

        // Second call with null cursor succeeds
        when(mockConnector.fetchMessages(any(), any(), isNull(), anyInt()))
                .thenReturn(FetchResult.of(List.of(), "fresh_delta_link_456", false));

        IngestionJob job = syncManager.synchronizeMailbox(mailbox.getId(), TriggerType.MANUAL_SYNC);

        assertNotNull(job);
        assertEquals("COMPLETED", job.getStatus());

        // Verify that fresh sync was invoked with null cursor
        verify(mockConnector, times(1)).fetchMessages(any(), any(), isNull(), eq(50));

        MailboxSyncState updatedState = syncStateRepository.findByMailboxId(mailbox.getId()).orElseThrow();
        assertEquals("fresh_delta_link_456", updatedState.getCursorValue());
    }
}
