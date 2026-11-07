package com.umis.mailbox.service;

import com.umis.common.exception.BadRequestException;
import com.umis.common.exception.ConflictException;
import com.umis.common.exception.ResourceNotFoundException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.mailbox.connector.MailboxConnector;
import com.umis.mailbox.connector.MailboxConnectorRegistry;
import com.umis.mailbox.connector.model.ConnectionResult;
import com.umis.mailbox.connector.model.ProviderCapability;
import com.umis.mailbox.connector.model.ProviderConnectionInit;
import com.umis.mailbox.dto.ConnectMailboxRequest;
import com.umis.mailbox.dto.MailboxDto;
import com.umis.mailbox.dto.MailboxHealthDto;
import com.umis.mailbox.dto.UpdateMailboxSettingsRequest;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.MailboxStatus;
import com.umis.mailbox.model.MailboxSyncState;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.mailbox.repository.MailboxCredentialsRepository;
import com.umis.mailbox.repository.MailboxSyncStateRepository;
import com.umis.security.EncryptionService;
import com.umis.security.ImapHostValidator;
import com.umis.security.OAuthStateStore;
import com.umis.security.TenantContext;
import com.umis.tenant.service.TenantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service managing mailbox connections, credentials, lifecycle, and health verification.
 */
@Service
public class MailboxService {

    private final MailboxConnectionRepository connectionRepository;
    private final MailboxCredentialsRepository credentialsRepository;
    private final MailboxSyncStateRepository syncStateRepository;
    private final MailboxConnectorRegistry connectorRegistry;
    private final EncryptionService encryptionService;
    private final TenantService tenantService;
    private final ImapHostValidator imapHostValidator;
    private final OAuthStateStore oauthStateStore;

    public MailboxService(
            MailboxConnectionRepository connectionRepository,
            MailboxCredentialsRepository credentialsRepository,
            MailboxSyncStateRepository syncStateRepository,
            MailboxConnectorRegistry connectorRegistry,
            EncryptionService encryptionService,
            TenantService tenantService,
            ImapHostValidator imapHostValidator,
            OAuthStateStore oauthStateStore
    ) {
        this.connectionRepository = connectionRepository;
        this.credentialsRepository = credentialsRepository;
        this.syncStateRepository = syncStateRepository;
        this.connectorRegistry = connectorRegistry;
        this.encryptionService = encryptionService;
        this.tenantService = tenantService;
        this.imapHostValidator = imapHostValidator;
        this.oauthStateStore = oauthStateStore;
    }

    public List<ProviderCapability> getProviders() {
        return connectorRegistry.getAllCapabilities();
    }

    public ProviderConnectionInit initiateOAuth(MailboxProvider provider, String redirectUri, String state, UUID tenantId) {
        UUID effectiveTenantId = resolveEffectiveTenantId(tenantId);
        tenantService.validateTenantAccess(effectiveTenantId);
        String secureState = (state != null && !state.isBlank() && !state.equals("default_state"))
                ? state
                : oauthStateStore.generateState(effectiveTenantId, provider, redirectUri);
        MailboxConnector connector = connectorRegistry.getConnector(provider);
        return connector.initiateConnection(redirectUri, secureState);
    }

    @Transactional
    public MailboxDto connectMailbox(ConnectMailboxRequest request) {
        UUID effectiveTenantId = resolveEffectiveTenantId(request.tenantId());
        tenantService.validateTenantAccess(effectiveTenantId);

        MailboxConnector connector = connectorRegistry.getConnector(request.provider());
        if (request.provider() == MailboxProvider.GENERIC_IMAP) {
            return connectImapMailbox(effectiveTenantId, request, connector);
        }

        return connectOAuthMailbox(effectiveTenantId, request, connector);
    }

    private MailboxDto connectImapMailbox(UUID tenantId, ConnectMailboxRequest req, MailboxConnector connector) {
        if (!StringUtils.hasText(req.emailAddress()) || !StringUtils.hasText(req.imapPassword())) {
            throw new BadRequestException("Email address and password are required for IMAP");
        }
        if (connectionRepository.existsByTenantIdAndEmailAddress(tenantId, req.emailAddress())) {
            throw new ConflictException("Mailbox '" + req.emailAddress() + "' is already connected for this tenant");
        }

        String strHost = req.imapHost() != null ? req.imapHost() : "localhost";
        imapHostValidator.validateHost(strHost);

        MailboxConnection connection = new MailboxConnection(
                tenantId, req.emailAddress(), req.displayName() != null ? req.displayName() : req.emailAddress(),
                MailboxProvider.GENERIC_IMAP, SyncStrategy.POLLING);
        if (req.pollingIntervalMinutes() != null && req.pollingIntervalMinutes() > 0) {
            connection.setPollingIntervalMinutes(req.pollingIntervalMinutes());
        }
        MailboxConnection savedConn = connectionRepository.save(connection);

        MailboxCredentials credentials = new MailboxCredentials(savedConn, "PASSWORD");
        credentials.setServerHost(strHost);
        credentials.setServerPort(req.imapPort() != null ? req.imapPort() : 993);
        credentials.setUseSsl(req.useSsl() == null || req.useSsl());
        credentials.setEncryptedSecret(encryptionService.encrypt(req.imapPassword()));
        credentialsRepository.save(credentials);

        syncStateRepository.save(new MailboxSyncState(savedConn));
        return MailboxDto.from(savedConn);
    }

    private MailboxDto connectOAuthMailbox(UUID tenantId, ConnectMailboxRequest req, MailboxConnector connector) {
        // The MOCK provider has no real external authorization server or redirect, so there is no
        // CSRF-style state to validate. Every real OAuth provider must present a valid, single-use,
        // tenant/provider/redirect-bound state token - this is not optional.
        if (req.provider() != MailboxProvider.MOCK) {
            oauthStateStore.validateAndConsume(req.state(), tenantId, req.provider(), req.redirectUri());
        }

        ConnectionResult result = connector.completeConnection(
                req.authorizationCode(), req.codeVerifier(), req.redirectUri());
        if (!result.success()) {
            throw new BadRequestException("Provider connection failed: " + result.errorMessage());
        }

        String email = StringUtils.hasText(req.emailAddress()) ? req.emailAddress() : result.emailAddress();
        if (connectionRepository.existsByTenantIdAndEmailAddress(tenantId, email)) {
            throw new ConflictException("Mailbox '" + email + "' is already connected for this tenant");
        }

        MailboxConnection connection = new MailboxConnection(
                tenantId, email, req.displayName() != null ? req.displayName() : result.displayName(),
                req.provider(), req.syncStrategy() != null ? req.syncStrategy() : SyncStrategy.POLLING);
        MailboxConnection savedConn = connectionRepository.save(connection);

        MailboxCredentials credentials = new MailboxCredentials(savedConn, "OAUTH2");
        credentials.setEncryptedAccessToken(encryptionService.encrypt(result.accessToken()));
        credentials.setEncryptedRefreshToken(encryptionService.encrypt(result.refreshToken()));
        credentials.setTokenExpiresAt(result.tokenExpiresAt());
        credentials.setScope(result.scope());
        credentialsRepository.save(credentials);

        syncStateRepository.save(new MailboxSyncState(savedConn));
        return MailboxDto.from(savedConn);
    }

    @Transactional(readOnly = true)
    public Page<MailboxDto> listMailboxes(UUID tenantId, Pageable pageable) {
        UUID effectiveTenantId = resolveEffectiveTenantId(tenantId);
        tenantService.validateTenantAccess(effectiveTenantId);
        // effectiveTenantId is only ever null for a GLOBAL_ADMIN who did not pick a specific
        // tenant (resolveEffectiveTenantId always returns a tenant-scoped user's own tenant
        // context, never null) - show mailboxes across all tenants rather than none.
        if (effectiveTenantId == null) {
            return connectionRepository.findAll(pageable).map(MailboxDto::from);
        }
        return connectionRepository.findByTenantId(effectiveTenantId, pageable).map(MailboxDto::from);
    }

    @Transactional(readOnly = true)
    public MailboxDto getMailboxById(UUID id) {
        MailboxConnection connection = connectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox not found with ID: " + id));
        tenantService.validateTenantAccess(connection.getTenantId());
        return MailboxDto.from(connection);
    }

    @Transactional
    public MailboxHealthDto checkMailboxHealth(UUID mailboxId) {
        MailboxConnection conn = connectionRepository.findById(mailboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox not found with ID: " + mailboxId));
        tenantService.validateTenantAccess(conn.getTenantId());

        MailboxCredentials creds = credentialsRepository.findByMailboxId(mailboxId).orElse(null);
        if (creds == null) {
            conn.setStatus(MailboxStatus.CONNECTION_ERROR);
            conn.setErrorMessage("Missing credentials");
            return new MailboxHealthDto(mailboxId, conn.getEmailAddress(), conn.getStatus(), false, false,
                    conn.getLastSuccessfulSyncAt(), conn.getFailureCount(), "Missing mailbox credentials");
        }

        MailboxConnector connector = connectorRegistry.getConnector(conn.getProvider());
        refreshCredentialsIfExpired(connector, creds);

        boolean isReachable = connector.testConnection(conn, creds);
        if (!isReachable && creds.getEncryptedRefreshToken() != null) {
            if (connector.refreshCredentials(creds)) {
                credentialsRepository.save(creds);
                isReachable = connector.testConnection(conn, creds);
            }
        }

        if (isReachable) {
            conn.setStatus(MailboxStatus.ACTIVE);
            conn.setErrorMessage(null);
        } else {
            conn.setStatus(MailboxStatus.CONNECTION_ERROR);
            conn.setFailureCount(conn.getFailureCount() + 1);
            conn.setErrorMessage("Mailbox unreachable or credentials invalid");
        }
        connectionRepository.save(conn);

        return new MailboxHealthDto(mailboxId, conn.getEmailAddress(), conn.getStatus(), isReachable, isReachable,
                conn.getLastSuccessfulSyncAt(), conn.getFailureCount(), conn.getErrorMessage());
    }

    private void refreshCredentialsIfExpired(MailboxConnector connector, MailboxCredentials credentials) {
        Instant expiry = credentials.getTokenExpiresAt();
        if (expiry != null && expiry.isBefore(Instant.now().plusSeconds(60))) {
            if (connector.refreshCredentials(credentials)) {
                credentialsRepository.save(credentials);
            }
        }
    }

    @Transactional
    public MailboxDto updateSettings(UUID mailboxId, UpdateMailboxSettingsRequest request) {
        MailboxConnection conn = connectionRepository.findById(mailboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox not found with ID: " + mailboxId));
        tenantService.validateTenantAccess(conn.getTenantId());

        if (StringUtils.hasText(request.displayName())) {
            conn.setDisplayName(request.displayName());
        }
        if (request.syncStrategy() != null) {
            conn.setSyncStrategy(request.syncStrategy());
        }
        if (request.pollingIntervalMinutes() != null && request.pollingIntervalMinutes() > 0) {
            conn.setPollingIntervalMinutes(request.pollingIntervalMinutes());
        }

        return MailboxDto.from(connectionRepository.save(conn));
    }

    @Transactional
    public void disconnectMailbox(UUID mailboxId) {
        MailboxConnection conn = connectionRepository.findById(mailboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox not found with ID: " + mailboxId));
        tenantService.validateTenantAccess(conn.getTenantId());

        MailboxCredentials creds = credentialsRepository.findByMailboxId(mailboxId).orElse(null);
        if (creds != null) {
            MailboxConnector connector = connectorRegistry.getConnector(conn.getProvider());
            try {
                connector.disconnect(conn, creds);
            } catch (Exception ignored) {
                // Best-effort provider disconnect
            }
        }

        conn.setStatus(MailboxStatus.DISCONNECTED);
        connectionRepository.save(conn);
    }

    private UUID resolveEffectiveTenantId(UUID requestedTenantId) {
        UUID contextId = TenantContext.getTenantId();
        if (contextId != null) {
            if (requestedTenantId != null && !requestedTenantId.equals(contextId)) {
                throw new UnauthorizedException("Access denied: Cannot specify or access another tenant: " + requestedTenantId);
            }
            return contextId;
        }
        return requestedTenantId;
    }
}
