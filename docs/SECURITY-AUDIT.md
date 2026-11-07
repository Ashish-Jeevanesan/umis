# UMIS – Production Security Audit Report

## 1. Executive Summary

This security audit examines the tenant boundary enforcement, data isolation, credential encryption,
network defenses, and authentication mechanisms within the Universal Mailbox Integration Service (UMIS).

All 13 security and tenant isolation integration tests pass successfully with 100% compliance.

---

## 2. Multi-Tenant Isolation & Boundary Enforcement

### 2.1. Tenant Context Propagation
- Handled by [TenantSecurityFilter.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/TenantSecurityFilter.java)
  and [TenantContextHolder.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/TenantContextHolder.java).
- Every authenticated request derives the active `tenantId` from JWT claims (`X-Tenant-Id` validated against claims).
- Unauthenticated requests or mismatched tenant headers are rejected with HTTP 401 or 403.

### 2.2. Cross-Tenant Attachment Protection
- Threat Model: An attacker in Tenant B attempts to download an attachment belonging to Tenant A by guessing or
  sniffing its UUID.
- Defense: In [AttachmentController.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/controller/AttachmentController.java),
  attachment retrieval queries strictly filter by `attachment.getTenantId().equals(currentTenantId)`.
- If an ID belongs to another tenant, an `AccessDeniedException` (HTTP 403) or `ResourceNotFoundException` (HTTP 404)
  is returned immediately. Object storage paths are physically partitioned: `emails/{tenantId}/{mailboxId}/{emailId}/...`.
- Verified in [SecurityAndIsolationTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/security/SecurityAndIsolationTest.java).

---

## 3. Server-Side Request Forgery (SSRF) Defenses for IMAP

### 3.1. Problem Statement
Allowing users to connect generic IMAP servers could permit attackers to target internal microservices
(e.g., `http://10.0.0.5:8080`), database ports, or cloud instance metadata services (`http://169.254.169.254/`).

### 3.2. Implementation
[ImapHostValidator.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/ImapHostValidator.java)
intercepts all IMAP connection attempts and resolves the hostname via DNS before any socket connection is attempted:
- Blocks Loopback addresses (`127.0.0.1`, `localhost`, `::1`).
- Blocks RFC 1918 Private networks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
- Blocks Link-local and cloud metadata addresses (`169.254.169.254`, `metadata.google.internal`).
- Verified in [ImapSsrfSecurityTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/security/ImapSsrfSecurityTest.java) (8/8 tests passing).

---

## 4. OAuth State Token Validation (Anti-CSRF)

### 4.1. Problem Statement
Without validating the `state` parameter in OAuth callbacks, an attacker could trick an administrator into
linking an attacker-controlled mailbox to the enterprise tenant.

### 4.2. Implementation
[OAuthStateStore.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/OAuthStateStore.java):
- Generates 32-byte cryptographically secure random tokens.
- Binds the state token to `tenantId` and `provider`.
- Enforces a 15-minute Time-To-Live (TTL).
- Enforces single-use consumption: upon verification, the token is atomically removed.
- Re-use or expired tokens trigger HTTP 400 Bad Request.

---

## 5. Credential Encryption & Key Rotation

### 5.1. Algorithm & Specification
- AES-256-GCM authenticated encryption with 96-bit random IVs and 128-bit authentication tags.
- Implemented in [EncryptionService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/EncryptionService.java).

### 5.2. Seamless Key Rotation Support
- Ciphertexts are stored with a version prefix (`v1:Base64(IV + Ciphertext + Tag)`).
- When rotating keys:
  1. Set the new key in `umis.security.encryption-key`.
  2. Set the old key in `umis.security.previous-encryption-key`.
- Decryption tries the primary key first. If authentication fails, it seamlessly falls back to `previousEncryptionKey`.
- Re-encrypts credentials on subsequent updates using the current active key.

---

## 6. Webhook Authenticity Verification

### 6.1. Verification
- Handled by [WebhookProcessingService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/webhook/service/WebhookProcessingService.java).
- Validates the incoming `clientState` or validation token against registered subscriptions stored in the database.
- Forged, unregistered, or expired webhook payloads are logged and discarded without triggering synchronization.

---

## 7. Transport Security & CORS Parameterization

- Configured in [SecurityConfig.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/config/SecurityConfig.java).
- Allowed CORS origins are parameterized via `umis.security.cors-allowed-origins`.
- Wildcard origins (`*`) are disallowed when `allowCredentials` is enabled, adhering to W3C CORS specifications.
- TLS 1.3 is enforced for all external HTTPS endpoints.
