# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Scope

UMIS (Universal Mailbox Integration Service) is a standalone, multi-tenant mailbox integration microservice. It connects to customer mailboxes (M365/Graph, Gmail, generic IMAP, Nylas/Unipile-style unified APIs, or a mock sandbox), normalizes incoming emails/attachments, stores large bodies/binaries in S3-compatible object storage, and publishes `EMAIL_INGESTED` domain events downstream via RabbitMQ.

**Hard boundary**: UMIS does not do PO/invoice extraction, ML classification, or any business-specific document parsing. It only ingests, normalizes, stores, and emits events — downstream consumer modules (`PO_PROCESSING`, `CRM`, `INVOICE_PROCESSING`) report their own outcomes back via independent acknowledgements. Do not add business-document logic here; it belongs in a consumer module.

Not a git repository — there is no commit history or branch workflow to follow here.

## Commands

Backend (from `backend/`):
```bash
mvn clean spring-boot:run      # run backend (port 8080 by default)
mvn clean test                 # full test suite (H2 in-memory, GreenMail for IMAP, WireMock for HTTP providers)
mvn test -Dtest=ClassName                     # single test class
mvn test -Dtest=ClassName#methodName          # single test method
```

Frontend (from `frontend/`):
```bash
npm install
npm run dev        # Vite dev server, port 3000, proxies /api -> http://localhost:8081
npm run build       # tsc type-check + production build
npm run preview
```

Combined launcher (from repo root, PowerShell): `.\start-services.ps1` (or double-click `start-services.bat`). It scans for free ports starting at 8080 (backend) / 3000 (frontend), **rewrites `frontend/vite.config.ts` and `frontend/.env` in place** with whatever ports it found plus the resolved backend proxy target, then launches both in separate consoles. Because of this, the proxy target in `vite.config.ts` and the port in `frontend/.env` are transient/generated — don't treat their current values as fixed, and be aware editing them manually will be overwritten the next time the launcher runs.

Swagger UI: `http://localhost:8080/swagger-ui.html` · Actuator health: `http://localhost:8080/actuator/health` (endpoints exposed: `health,info,metrics,prometheus`).

Docker: `docker/docker-compose.yml` + `docker/Dockerfile.backend` / `Dockerfile.frontend` bring up the full stack (Postgres, RabbitMQ, MinIO) for container-based runs.

## Architecture

**Flow**: Provider APIs → `MailboxConnector` implementations → ingestion engine (dedup via SHA-256, AES-256 credential encryption, binary offload to pluggable object storage) → same-transaction write to `email_messages` + `event_outbox` → async dispatch to RabbitMQ (`umis.events` topic exchange, with DLX/DLQ) → downstream consumer modules POST acknowledgements back to `/emails/{id}/acknowledgements`.

**Object storage** (`backend/src/main/java/com/umis/email/service/`): `ObjectStorageClient` interface with two `@ConditionalOnProperty`-gated implementations selected by `umis.storage.type`/`UMIS_STORAGE_TYPE` — `LocalFilesystemStorageClient` (default; atomic temp-file+rename writes, streaming reads, path-traversal-safe) and `S3ObjectStorageClient` (MinIO/AWS S3). Exactly one is ever constructed; in `LOCAL` mode the AWS SDK client isn't instantiated at all. `EmailStorageService` is the only caller of either — see `docs/STORAGE-ABSTRACTION.md`.

**Provider abstraction** (`backend/src/main/java/com/umis/mailbox/connector/`): everything provider-specific is isolated behind the `MailboxConnector` interface (`initiateConnection`, `completeConnection`, `refreshCredentials`, `fetchMessages`, `fetchMessage`, `fetchAttachments`, `startMonitoring`/`stopMonitoring`, `disconnect`). Implementations live in `connector/impl/`: `MicrosoftGraphConnector` (PKCE OAuth, delta queries, webhooks), `GmailConnector` (+ `GmailPayloadParser`, OAuth2, History API, Pub/Sub watch), `GenericImapConnector` (Jakarta/Angus Mail, SSL/TLS, UID-based sync), `UnifiedEmailApiConnector`, and `MockMailboxConnector` (zero-dependency local/demo mode). The rest of the system — ingestion, REST APIs, dashboards — only ever touches the connector-agnostic `NormalizedEmail`/`NormalizedAttachment` model (`connector/model/`); never add provider-specific types or branching outside this package.

**Backend module layout** (`backend/src/main/java/com/umis/`), one package per bounded concern: `tenant` (orgs, membership, roles), `mailbox` (connections, credentials, connector registry — `MailboxConnectorRegistry` dispatches by `MailboxProvider`), `email` (ingestion, messages, recipients, attachments), `event` (outbox + publisher, plus `event/consumer/*` for downstream acknowledgement tracking), `webhook` (provider push payload intake), `dashboard` (global/tenant KPI aggregation), `audit`, `security`, `config`, `common` (shared API/exception/util). Each feature package follows `controller/dto/model/repository/service` sub-layering where applicable.

**Multi-tenancy & security**: JWT-based auth (`JwtAuthenticationFilter`, `JwtTokenProvider`); the active tenant is carried per-request in a thread-local, `TenantContext` (`security/TenantContext.java`) — always clear it after use, never trust a client-supplied tenant ID without verifying JWT-derived membership. Roles (`security/RoleConstants.java`): `GLOBAL_ADMIN` (cross-tenant), `TENANT_ADMIN`, `TENANT_VIEWER`, `SERVICE_CLIENT`, with matching Spring Security SpEL constants (`HAS_GLOBAL_ADMIN`, `HAS_TENANT_ADMIN`, `HAS_TENANT_ACCESS`) for `@PreAuthorize`. Mailbox credentials are AES-256 encrypted at rest (`security/EncryptionService.java`); IMAP host connections are SSRF-guarded (`security/ImapHostValidator.java`, see `security/ImapSsrfSecurityTest.java`).

**Database**: PostgreSQL 16 in prod, H2 (Postgres-compatibility mode) for tests. Flyway migrations in `backend/src/main/resources/db/migration`: `V1` = core tenant/mailbox schema, `V2` = email ingestion + consumer ack schema, `V3` = outbox/webhook/metrics/audit schema. See `docs/DATABASE.md` for the full 17-table inventory and key constraints (`uk_tenant_mailbox_email`, `uk_mailbox_provider_msg` for dedup, `uk_email_consumer` for independent per-consumer status, `idx_email_tenant_received`, `idx_outbox_status_occurred`).

**Transactional outbox**: email persistence and the corresponding `event_outbox` row are written in one DB transaction; a separate publisher polls/dispatches outbox rows to RabbitMQ, guaranteeing at-least-once delivery without dual-write races. When touching ingestion write paths, preserve this same-transaction invariant.

**Frontend** (`frontend/src/`): React 18 + TypeScript + Vite, TanStack Query for server state, Tailwind for styling, Recharts for charts. `api/client.ts` is the API layer (`api/mockData.ts` backs demo/mock mode), `context/AuthContext.tsx` holds auth state, `components/` is organized by feature (`dashboard`, `emails`, `mailboxes`, `tenants`, `health`, `layout`, `common`). Two dashboard surfaces mirror the two admin roles: `GlobalOverview` (cross-tenant) vs `TenantOverview` (single-org).

**Config profiles**: `application.yml` (base/dev defaults), `application-dev.yml`, `application-local.yml`, and `application-test.yml` (under `src/test/resources`, used by `mvn test`). `UmisProperties` binds the `umis.*` custom config tree (JWT secret/expiry, encryption key, S3/MinIO storage, RabbitMQ exchange/queue names, sync polling/retry/lock settings) — add new custom settings there rather than reading `@Value` ad hoc.

**Testing conventions**: `GenericImapConnectorTest`/`GmailConnectorTest`/`MicrosoftGraphConnectorTest` use GreenMail (IMAP) and WireMock (HTTP provider APIs) rather than live external services. `TenantIsolationTest` and `SecurityAndIsolationTest` guard cross-tenant data leakage — any change touching tenant scoping, repositories, or JWT/role logic should keep these passing. `SynchronizationReliabilityTest` and `MessagingAndOutboxTest` cover incremental sync cursors/dedup and outbox/DLQ guarantees respectively.

## Further reading

`docs/` has one file per concern — read the relevant one before making non-trivial changes in that area: `ARCHITECTURE.md`, `DATABASE.md`, `MAILBOX-INTEGRATIONS.md`, `EVENT-CONTRACTS.md`, `API.md`, `DEPLOYMENT.md`, `SECURITY.md`, `IMPLEMENTATION-STATUS.md`, `STORAGE-ABSTRACTION.md` (object storage interface, both backends' guarantees, S3↔LOCAL migration), `LOCAL-DEVELOPMENT.md` (running the full pipeline on Windows without MinIO/S3).
