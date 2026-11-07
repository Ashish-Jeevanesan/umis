# UMIS Security & Threat Model

## 1. Multi-Tenant Isolation Model

Tenant isolation is enforced across three distinct layers:
1. **Cryptographic Identity Layer**: User authentication produces a cryptographically signed JWT containing verified `tenantId` and `role` claims.
2. **Context Resolution Layer**: `TenantContext` extracts the tenant ID from the verified token. If a client attempts to supply a different tenant ID via query or body parameters, an `UnauthorizedException` is immediately thrown.
3. **Repository Layer**: All tenant-owned tables (`mailbox_connections`, `email_messages`, `email_attachments`, `ingestion_jobs`) enforce `tenant_id` foreign keys and tenant-scoped repository queries.

## 2. OAuth Credential Security

- **PKCE Flow**: Implements OAuth 2.0 Authorization Code flow with Proof Key for Code Exchange (RFC 7636) to prevent authorization code interception.
- **State Verification**: Uses unique cryptographically random state tokens to thwart CSRF attacks during provider redirects.
- **Envelope Encryption**: Access tokens, refresh tokens, and passwords are encrypted prior to database persistence using **AES-256-GCM** with a distinct 96-bit Initialization Vector (IV) per record.
- **Zero Token Leakage**: Mailbox credentials and raw tokens are excluded from DTOs and API responses. Application logs filter sensitive headers and tokens.

## 3. Attachment Security & Storage Offloading

- **No PostgreSQL Blobs**: Email bodies and attachments are strictly written to object storage, preventing database bloat and memory exhaustion attacks.
- **Size Quota Enforcement**: Attachments are checked against `max-file-size-bytes` (default 25 MB) before storage.
- **Integrity Verification**: Every attachment computes and records a SHA-256 digest (`file_hash`) upon receipt.
- **Malware Scanning Hook**: `EmailAttachment.scan_status` tracks scan progression (`PENDING`, `CLEAN`, `INFECTED`) allowing integration with ClamAV or external antivirus pipelines.

## 4. Webhook Security

- **Provider Verification**: Microsoft Graph requests require client state matching or handshake validation tokens.
- **Asynchronous Execution**: Webhook listeners record the notification and dispatch synchronization to background workers, preventing denial-of-service (DoS) from long-running downloads.

## 5. SSRF Defense in IMAP Configurations

- IMAP hostnames are validated against disallowed loopback/link-local ranges (`127.0.0.1`, `169.254.169.254`, `localhost` in production) to prevent Server-Side Request Forgery against cloud metadata services.
