# UMIS Database Design & Migration Specification

## 1. Overview & Technology

- **Database Engine**: PostgreSQL 16+ (H2 in compatibility mode for fast test runs)
- **Primary Keys**: UUID (`gen_random_uuid()`)
- **Timestamps**: Timezone-aware `TIMESTAMP WITH TIME ZONE`
- **Migration Management**: Flyway database migrations (`V1`, `V2`, `V3`)

## 2. Table Inventory (17 Tables)

### Core Multi-Tenant & Mailbox Schemas (Flyway V1)
1. `tenants`: Primary organization records (`id`, `code`, `name`, `status`, `created_at`, `updated_at`).
2. `users`: System users and identities (`id`, `email`, `full_name`, `password_hash`, `system_role`, `active`).
3. `tenant_users`: Organization membership mapping (`tenant_id`, `user_id`, `role`).
4. `mailbox_connections`: Connected mailboxes (`tenant_id`, `email_address`, `provider`, `status`, `sync_strategy`, `polling_interval_minutes`).
5. `mailbox_credentials`: AES-256 encrypted access tokens, refresh tokens, and server secrets.
6. `mailbox_sync_state`: Incremental cursors, delta links, Gmail history IDs, IMAP UIDs, and concurrency locks (`sync_lock_token`).
7. `mailbox_subscriptions`: Provider push webhook subscriptions and expiry tracking.

### Email Ingestion & Downstream Consumer Schemas (Flyway V2)
8. `email_messages`: Normalized message metadata, sender, snippet, S3 body references, content hash.
9. `email_recipients`: Normalized TO, CC, and BCC recipient records.
10. `email_attachments`: Attachment metadata, MIME types, file sizes, SHA-256 hashes, and S3 storage keys.
11. `ingestion_jobs`: Ingestion batch tracking (`trigger_type`, items discovered/ingested/skipped/failed).
12. `consumer_registrations`: Registered downstream systems (e.g. `PO_PROCESSING`, `CRM`, `INVOICE_PROCESSING`).
13. `email_processing_status`: Independent downstream outcome tracking per consumer per email.

### Outbox, Webhooks, Metrics & Audit Schemas (Flyway V3)
14. `event_outbox`: Transactional outbox table for reliable RabbitMQ dispatch (`event_id`, `payload`, `status`).
15. `webhook_events`: Raw provider push payloads for auditing and asynchronous worker processing.
16. `daily_ingestion_metrics`: Daily aggregated rollup table preventing expensive full-table scans.
17. `audit_logs`: Administrative actions and security audit trails.

## 3. Important Indexes & Constraints

- `uk_tenant_mailbox_email`: Unique constraint on `(tenant_id, email_address)` preventing duplicate mailbox connections.
- `uk_mailbox_provider_msg`: Unique constraint on `(mailbox_id, provider_message_id)` enforcing message deduplication.
- `uk_email_consumer`: Unique constraint on `(email_id, consumer_code)` allowing independent consumer status updates.
- `idx_email_tenant_received`: Composite index on `(tenant_id, received_at DESC)` for high-performance dashboard activity tables.
- `idx_outbox_status_occurred`: Composite index on `(status, occurred_at)` for outbox dispatch polling.
