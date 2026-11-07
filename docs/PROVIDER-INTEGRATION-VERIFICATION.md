# UMIS – Provider Integration Verification Report

## 1. Scope & Objective

This report details the integration testing and verification of real email providers for UMIS:
1. **Microsoft 365 / Microsoft Graph API**
2. **Google Workspace / Gmail REST API**
3. **Generic IMAP4rev1 (RFC 3501)**

All connectors were audited against production failure modes and verified using WireMock HTTP stubs
and an embedded GreenMail IMAP server.

---

## 2. Microsoft Graph Connector Verification

The [MicrosoftGraphConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/MicrosoftGraphConnector.java)
integrates with the Microsoft Graph REST API v1.0.

### 2.1. OAuth 2.0 Token Exchange
- Token endpoint: `https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token`
- Verified: Exchanging authorization code for `access_token`, `refresh_token`, and `expires_in`.
- Refresh token flow: Automatically refreshes expired access tokens before API invocation.

### 2.2. Delta Query Synchronization & Pagination
- Delta endpoint: `https://graph.microsoft.com/v1.0/me/mailFolders('Inbox')/messages/delta`
- Select query: `id,conversationId,subject,from,toRecipients,ccRecipients,hasAttachments,receivedDateTime,body`
- Pagination: Traverses `@odata.nextLink` until the final page returns `@odata.deltaLink`.
- The `@odata.deltaLink` is persisted as the cursor in `t_mailbox_sync_state.cursor_value`.

### 2.3. Resync on Expired Delta Cursor (HTTP 410)
- Graph returns HTTP 410 with error code `resyncRequired` when a delta token is older than 30 days.
- Hardened Behavior: Throws `MailboxIntegrationException("resyncRequired")`. [EmailSyncManager.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/service/EmailSyncManager.java)
  catches this, clears the cursor, and executes a full baseline sync.

### 2.4. Rate Limiting Backoff (HTTP 429)
- Graph returns HTTP 429 when tenant thresholds are exceeded.
- Verified: `MicrosoftGraphConnector` parses the `Retry-After` header and pauses execution before retrying.

*Test Suite*: [MicrosoftGraphConnectorTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/mailbox/connector/MicrosoftGraphConnectorTest.java) (5/5 Passing)

---

## 3. Gmail REST Connector Verification

The [GmailConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/GmailConnector.java)
interacts with Google's Gmail API v1.

### 3.1. OAuth 2.0 Authorization & Scopes
- Token endpoint: `https://oauth2.googleapis.com/token`
- Requested scopes: `https://www.googleapis.com/auth/gmail.readonly`

### 3.2. Incremental Sync via History API
- Baseline sync uses `users/me/messages?q=in:inbox`.
- Incremental sync uses `users/me/history?startHistoryId={cursor}`.
- Expired History ID Handling: If Google returns 404 or "History id not found", the connector flags the cursor
  as expired, prompting an immediate fallback to baseline listing.

### 3.3. MIME Parsing & Attachment Extraction
- Uses [GmailPayloadParser.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/GmailPayloadParser.java).
- Traverses multipart hierarchies (`multipart/alternative`, `multipart/mixed`).
- Decodes Base64URL encoded bodies for `text/plain` and `text/html`.
- Extracts attachment metadata (`partId`, `filename`, `mimeType`, `size`) and raw payload data.

*Test Suite*: [GmailConnectorTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/mailbox/connector/GmailConnectorTest.java) (4/4 Passing)

---

## 4. Generic IMAP Connector Verification

The [GenericImapConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/GenericImapConnector.java)
provides RFC 3501 compliance using Jakarta Mail.

### 4.1. Live Protocol Verification with GreenMail
- Verified against live GreenMail IMAP server listening on test port 3143.
- Successfully performs authentication, mailbox selection (`INBOX`), and header retrieval.

### 4.2. UID Incremental Sync
- Retrieves messages with `UIDFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID)`.
- Enforces strict `uid > lastUid` filtering to eliminate duplicate ingestion on non-conformant IMAP servers.
- Tracks `highestUidSeen` and stores it as the cursor string.

### 4.3. SSRF Protection
- Validates host against [ImapHostValidator.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/security/ImapHostValidator.java)
  prior to opening any TCP socket.
- Blocks `localhost`, `127.0.0.1`, RFC 1918 private subnets, and cloud metadata (`169.254.169.254`).

*Test Suites*:
- [GenericImapConnectorTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/mailbox/connector/GenericImapConnectorTest.java) (3/3 Passing)
- [ImapSsrfSecurityTest.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/test/java/com/umis/security/ImapSsrfSecurityTest.java) (8/8 Passing)

---

## 5. External Credentials Required for Production Deployment

For live enterprise production deployment, the following external credentials must be supplied:

| Provider | Required Credential / Configuration Item | Source |
| :--- | :--- | :--- |
| **Microsoft 365** | `umis.oauth.microsoft.client-id` | Azure Portal App Registration |
| **Microsoft 365** | `umis.oauth.microsoft.client-secret` | Azure Portal Certificates & Secrets |
| **Microsoft 365** | Application Redirect URI | `https://<domain>/api/v1/mailboxes/oauth/callback` |
| **Gmail** | `umis.oauth.google.client-id` | Google Cloud Console OAuth 2.0 Client |
| **Gmail** | `umis.oauth.google.client-secret` | Google Cloud Console OAuth 2.0 Secret |
| **Gmail** | Google Pub/Sub Topic & Service Account | Google Cloud IAM for push notifications |
| **Generic IMAP** | Customer IMAP host, port, credentials | Target mail server admin |
