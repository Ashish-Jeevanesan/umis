# UMIS Mailbox Integrations Guide

## 1. Provider Adapter Architecture

UMIS exposes all email providers through the common `MailboxConnector` interface:
```java
public interface MailboxConnector {
    MailboxProvider getProvider();
    ProviderCapability getCapability();
    ProviderConnectionInit initiateConnection(String redirectUri, String state);
    ConnectionResult completeConnection(String authorizationCode, String codeVerifier, String redirectUri);
    boolean refreshCredentials(MailboxCredentials credentials);
    boolean testConnection(MailboxConnection connection, MailboxCredentials credentials);
    FetchResult fetchMessages(MailboxConnection connection, MailboxCredentials credentials, String cursor, int maxResults);
    ...
}
```

## 2. Microsoft 365 / Outlook (Microsoft Graph API)

### Azure App Registration Setup
1. Register an application in Microsoft Entra ID (Azure AD).
2. Configure Platform: Web Redirect URI: the frontend SPA origin, e.g. `http://localhost:3000/` (the OAuth
   redirect is captured client-side by the admin dashboard, not by a backend endpoint - see section 4 below).
3. Add Delegated or Application Permissions:
   - `Mail.Read`
   - `offline_access`
4. Create a Client Secret and set environment variables:
   - `AZURE_CLIENT_ID=<client_id>`
   - `AZURE_CLIENT_SECRET=<client_secret>`
   - `AZURE_TENANT_ID=common` (or specific Azure tenant UUID)
5. **Delta Query Synchronization**: Uses `@odata.deltaLink` to fetch only new/modified messages since previous cursor.
6. **Webhook Renewal**: Graph subscriptions expire after ~3 days and are renewed by scheduled task.

## 3. Gmail / Google Workspace (Gmail REST API)

### Google Cloud Console Setup
1. Create a project in Google Cloud Console.
2. Enable the **Gmail API**.
3. Configure OAuth consent screen with scope: `https://www.googleapis.com/auth/gmail.readonly`.
4. Create OAuth 2.0 Web Client Credentials:
   - Authorized redirect URI: the frontend SPA origin, e.g. `http://localhost:3000/` (see section 4 below).
5. Set environment variables:
   - `GOOGLE_CLIENT_ID=<client_id>`
   - `GOOGLE_CLIENT_SECRET=<client_secret>`
6. **History API & Pub/Sub Watch**: Push notifications published to Cloud Pub/Sub topic and forwarded to `/api/v1/webhooks/google`. Watch renewed every 7 days.

## 4. OAuth Connect Flow (Admin Dashboard)

Microsoft 365 and Gmail use the authorization-code flow end to end through the admin UI, with no dedicated
backend callback endpoint:

1. `GET /api/v1/mailboxes/oauth/initiate?provider=...&redirectUri=...` (requires `TENANT_ADMIN`/`GLOBAL_ADMIN`)
   builds the provider's real authorization URL using the configured `AZURE_CLIENT_ID`/`GOOGLE_CLIENT_ID` and a
   server-generated, tenant+provider-bound `state` token (`OAuthStateStore`, 15-minute TTL).
2. The frontend stashes the chosen tenant, provider, `codeVerifier`, `redirectUri`, and `state` in
   `sessionStorage`, then navigates the browser to that authorization URL.
3. After the user consents, the provider redirects the browser back to `redirectUri` (the frontend's own
   origin) with `?code=...&state=...`. The SPA picks this up on load, matches it against the stashed
   session state, and calls `POST /api/v1/mailboxes/connect` with the real `authorizationCode`.
4. `MailboxService.connectOAuthMailbox` validates and consumes the `state` (CSRF protection), then
   `GmailConnector`/`MicrosoftGraphConnector.completeConnection` performs the real token exchange.

Because the redirect target is the frontend's own origin rather than a fixed backend route, the registered
redirect URI must exactly match wherever the frontend is actually served (see `AZURE_REDIRECT_URI` /
`GOOGLE_REDIRECT_URI` in `.env.example`) — update the app registration if you move the frontend to a
different host or port.

## 5. Generic IMAP Integration

- **Supported Protocols**: IMAP over SSL/TLS (default port 993) or STARTTLS (port 143).
- **Authentication**: Direct username/password or application-specific passwords.
- **Synchronization**: Uses Jakarta Mail Angus implementation with `UIDFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID)` for incremental fetch.

## 6. Simulated Mock Provider (Zero External Dependencies)

- Select `MOCK` provider in UI or pass `provider: MOCK` via API.
- Generates simulated incoming business order emails, PDF/Excel attachments, and incremental cursors.
- Perfect for automated CI tests, developer local setup, and user demonstrations without real customer credentials.
