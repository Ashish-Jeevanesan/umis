# UMIS – Change Log & Implementation History

## 1. Overview

This document records all changes, defect fixes, security hardening, automated test suites, and infrastructure
improvements completed during **Phase 2: Production Readiness and Real-World Integration Audit** of the
Universal Mailbox Integration Service (UMIS), and details the exact current operational state.

---

## 2. Detailed Change History

### 2.1. Security & Data Isolation
- **IMAP SSRF Prevention**:
  - Implemented [ImapHostValidator.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/ImapHostValidator.java)
    to inspect host DNS records before TCP socket connections.
  - Blocks loopback (`127.0.0.1`, `localhost`), RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`,
    `192.168.0.0/16`), and cloud metadata services (`169.254.169.254`, `metadata.google.internal`).
  - Integrated into [GenericImapConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/GenericImapConnector.java)
    and [MailboxService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/service/MailboxService.java).
- **OAuth CSRF State Validation**:
  - Implemented [OAuthStateStore.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/OAuthStateStore.java)
    providing cryptographically secure random state tokens with 15-minute TTL.
  - Binds state tokens to tenant ID and provider with single-use invalidation upon verification.
- **AES-256-GCM Key Rotation**:
  - Hardened [EncryptionService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/EncryptionService.java)
    to prefix ciphertexts with version tags (`v1:`) and fallback to `previousEncryptionKey`.
- **Cross-Tenant Attachment Protection**:
  - Validated strict tenant isolation in [AttachmentController.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/controller/AttachmentController.java)
    and [EmailStorageService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/service/EmailStorageService.java).
- **CORS Parameterization**:
  - Parameterized allowed CORS origins via `umis.security.cors-allowed-origins` in
    [SecurityConfig.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/config/SecurityConfig.java).
- **Webhook Authenticity Enforcement**:
  - Updated [WebhookProcessingService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/webhook/service/WebhookProcessingService.java)
    to reject unauthenticated payloads lacking registered subscription tokens.

### 2.2. Provider Integrations & Normalized Ingestion
- **Microsoft Graph Connector**:
  - Delta queries, `@odata.nextLink`/`deltaLink` pagination, and token refreshes in
    [MicrosoftGraphConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/MicrosoftGraphConnector.java).
  - Handles HTTP 410 Gone (`resyncRequired`) with cursor invalidation and automatic baseline re-sync.
  - Handles HTTP 429 Too Many Requests with `Retry-After` backoff.
- **Gmail REST Connector**:
  - Created [GmailPayloadParser.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/GmailPayloadParser.java)
    to traverse multipart RFC 2822 payload hierarchies, decoding Base64URL `text/plain`, `text/html`, and attachments.
  - History API cursor tracking and expired historyId detection in
    [GmailConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/GmailConnector.java).
- **Generic IMAP Connector**:
  - Verified live IMAP protocol with GreenMail in
    [GenericImapConnectorTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/mailbox/connector/GenericImapConnectorTest.java).
  - Enforced strict `uid > lastUid` message filtering to eliminate duplicates on non-standard servers.

### 2.3. Transactional Reliability & Concurrency Control
- **Transactional Outbox Rollback**:
  - Fixed [EmailIngestionService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/service/EmailIngestionService.java)
    to re-throw outbox persistence failures, triggering `@Transactional` rollback to avoid orphan records.
- **RabbitMQ Relay Retry Backoff**:
  - Implemented retry counter and exponential backoff in
    [OutboxRelayScheduler.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/event/publisher/OutboxRelayScheduler.java).
- **Atomic Mailbox Sync Concurrency**:
  - Added atomic SQL conditional updates (`acquireLock`/`releaseLock`) with a 15-minute expiration safeguard in
    [MailboxSyncStateRepository.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/repository/MailboxSyncStateRepository.java)
    and [EmailSyncManager.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/service/EmailSyncManager.java).
- **Consumer Idempotency & Multi-Consumer Tracking**:
  - Guaranteed idempotent handling of duplicate consumer acknowledgements in
    [ConsumerService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/consumer/service/ConsumerService.java).
  - Supported independent processing statuses per consumer for shared ingested emails.

### 2.4. Dashboard Metric Accuracy
- Refactored [DashboardService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/dashboard/service/DashboardService.java)
  to derive the `parsed` metric from real downstream consumer acknowledgements and compute independent aggregate counts.

### 2.5. Automated Test Suites (39 Tests / 100% Pass)
- Added Mockito and WireMock standalone tests for Microsoft Graph and Gmail.
- Added GreenMail integration test for IMAP.
- Added SSRF test suite ([ImapSsrfSecurityTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/security/ImapSsrfSecurityTest.java)).
- Added security and boundary tests ([SecurityAndIsolationTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/security/SecurityAndIsolationTest.java)).
- Added messaging reliability tests ([MessagingAndOutboxTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/event/MessagingAndOutboxTest.java)).
- Added concurrency locking tests ([SynchronizationReliabilityTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/email/SynchronizationReliabilityTest.java)).
- Added dashboard accuracy tests ([DashboardAccuracyTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/dashboard/DashboardAccuracyTest.java)).

### 2.6. Production Documentation Deliverables
Generated 5 technical reports in [`docs/`](file:///D:/Ashish_P/Project%20X/UMIS/docs):
- [docs/PRODUCTION-READINESS-AUDIT.md](file:///D:/Ashish_P/Project%20X/UMIS/docs/PRODUCTION-READINESS-AUDIT.md)
- [docs/PROVIDER-INTEGRATION-VERIFICATION.md](file:///D:/Ashish_P/Project%20X/UMIS/docs/PROVIDER-INTEGRATION-VERIFICATION.md)
- [docs/SECURITY-AUDIT.md](file:///D:/Ashish_P/Project%20X/UMIS/docs/SECURITY-AUDIT.md)
- [docs/FAILURE-RECOVERY-TESTS.md](file:///D:/Ashish_P/Project%20X/UMIS/docs/FAILURE-RECOVERY-TESTS.md)
- [docs/PRODUCTION-DEPLOYMENT-READINESS.md](file:///D:/Ashish_P/Project%20X/UMIS/docs/PRODUCTION-DEPLOYMENT-READINESS.md)

### 2.7. Service Launcher & Port Discovery Enhancements
- **Fixed `TcpListener.Dispose` in PowerShell 5.1**:
  Replaced with `$tcpListener.Stop()` in
  [start-services.ps1](file:///D:/Ashish_P/Project%20X/UMIS/start-services.ps1).
- **Fixed PowerShell `-D` Parameter Splitting for Maven**:
  Enclosed Maven arguments in single quotes:
  `mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=...'`.
- **Fixed `PATH` String Interpolation**:
  Preserved JDK 21 OpenJDK path across PowerShell process arguments.
- **Enhanced Port Detection with `.NET IPGlobalProperties`**:
  Detects listeners bound strictly to `127.0.0.1:8080`, allowing automatic fallback to open port `8081`.
- **Standalone `local` Profile with Embedded Persistent Database**:
  - Created [application-local.yml](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/resources/application-local.yml)
    using an embedded H2 persistent file database (`./data/umis_db`) with PostgreSQL compatibility mode.
  - Made H2 available at runtime in [pom.xml](file:///D:/Ashish_P/Project%20X/UMIS/backend/pom.xml).
  - Added auto-detection of port 5432 in [start-services.ps1](file:///D:/Ashish_P/Project%20X/UMIS/start-services.ps1):
    selects `dev` profile if PostgreSQL is active, or `local` profile if PostgreSQL is absent.

### 2.8. Frontend Authentication & 403 Forbidden Resolution
- Identified root cause of `HTTP 403 Forbidden` on `GET /api/v1/tenants`: requests were sent without a JWT Bearer token
  when `localStorage` was unpopulated.
- Added `authenticateSession()` and automatic Bearer token resolution to
  [client.ts](file:///D:/Ashish_P/Project%20X/UMIS/frontend/src/api/client.ts).
- Synchronized session tokens on mount and role switch in
  [AuthContext.tsx](file:///D:/Ashish_P/Project%20X/UMIS/frontend/src/context/AuthContext.tsx).
- Verified with `npm run build` (clean Vite build, zero errors).

---

## 3. Current Operational State (Where We Are Now)

| Subsystem | Current State | Notes |
| :--- | :--- | :--- |
| **Backend Service** | Ready / Verified | Boots with Java 21 on available port (8080/8081). Standalone mode active. |
| **Database** | Ready / Operational | Auto-detects PostgreSQL on 5432; falls back to embedded H2 in `./backend/data/`. |
| **Frontend Dashboard** | Ready / Verified | Compiles cleanly; proxies API calls; auto-authenticates with JWT. |
| **Dual-Console Launcher** | Ready / Operational | Run [start-services.bat](file:///D:/Ashish_P/Project%20X/UMIS/start-services.bat) for one-click startup. |
| **Automated Tests** | 39 / 39 Passing | Run `mvn test` in `backend` to re-verify anytime. |
| **Provider Integrations** | Verified with Mocks | Microsoft Graph, Gmail, and IMAP protocol verified. Live credentials needed for prod. |
| **Open Tasks / Next Steps** | Ready for Deployment | Configure production cloud secrets (Azure AD / Google Cloud) when deploying to live env. |
