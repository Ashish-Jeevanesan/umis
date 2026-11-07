# UMIS REST API Specification

## 1. Authentication & Headers

Base Path: `/api/v1`

All requests except public webhooks and auth endpoints require a Bearer token:
`Authorization: Bearer <jwt_token>`

For Global Administrators operating across organizations:
`X-Tenant-Id: <tenant-uuid>` (optional context override)

## 2. API Endpoint Inventory

### Authentication
- `POST /api/v1/auth/login`: Authenticate email & password, returns JWT token with tenant claims.
- `GET /api/v1/auth/me`: Get current authenticated user profile and roles.

### Tenant Management (Global Admin)
- `POST /api/v1/tenants`: Create a new organization tenant.
- `GET /api/v1/tenants`: List all tenants with status filtering and pagination.
- `GET /api/v1/tenants/{id}`: Get tenant details by ID.
- `PATCH /api/v1/tenants/{id}`: Update tenant organization details.
- `POST /api/v1/tenants/{id}/suspend`: Suspend a tenant organization.
- `POST /api/v1/tenants/{id}/activate`: Activate a suspended organization.

### Mailbox Management
- `GET /api/v1/mailboxes/providers`: List supported providers and their capabilities.
- `GET /api/v1/mailboxes/oauth/initiate`: Initiate provider OAuth authorization URL.
- `POST /api/v1/mailboxes/connect`: Connect a mailbox via OAuth code or direct IMAP credentials.
- `GET /api/v1/mailboxes`: List connected mailboxes for the authenticated tenant.
- `GET /api/v1/mailboxes/{id}`: Get mailbox connection details.
- `GET /api/v1/mailboxes/{id}/health`: Test connectivity and credentials health.
- `POST /api/v1/mailboxes/{id}/sync`: Trigger immediate manual synchronization.
- `PATCH /api/v1/mailboxes/{id}/settings`: Update polling interval or sync strategy.
- `DELETE /api/v1/mailboxes/{id}`: Disconnect mailbox and stop monitoring.

### Email Ingestion & Attachments
- `GET /api/v1/emails`: List ingested emails with pagination, mailbox filter, and status filter.
- `GET /api/v1/emails/{id}`: Get email details including recipients and attachments.
- `GET /api/v1/emails/{id}/attachments`: List attachment metadata.
- `GET /api/v1/emails/{id}/attachments/{attachmentId}`: Securely stream binary attachment from S3.

### Downstream Consumer Registration & Acknowledgements
- `GET /api/v1/consumers`: List registered consumer applications.
- `POST /api/v1/consumers`: Register a new consumer application module.
- `POST /api/v1/emails/{emailId}/acknowledgements`: Record downstream processing outcome.
- `GET /api/v1/emails/{emailId}/acknowledgements`: Get processing outcomes across all consumers.

### Operational Dashboards
- `GET /api/v1/dashboard/global`: Global administrator KPI cards, distribution, and recent tenants.
- `GET /api/v1/dashboard/global/trends`: Global daily ingestion and downstream outcome trends.
- `GET /api/v1/dashboard/tenant`: Tenant-scoped KPI cards and mailbox summary.
- `GET /api/v1/dashboard/tenant/trends`: Tenant daily ingestion and downstream trends.
- `GET /api/v1/dashboard/tenant/mailboxes`: Tenant mailboxes with health indicators.
- `GET /api/v1/dashboard/health`: Infrastructure telemetry (Postgres, RabbitMQ, S3, Workers).

### Webhook Listeners (Unauthenticated, Provider-Verified)
- `POST /api/v1/webhooks/microsoft`: Microsoft Graph change notification listener & handshake.
- `POST /api/v1/webhooks/google`: Google Cloud Pub/Sub Gmail watch listener.
- `POST /api/v1/webhooks/unified-provider`: Unified email API webhook listener.
