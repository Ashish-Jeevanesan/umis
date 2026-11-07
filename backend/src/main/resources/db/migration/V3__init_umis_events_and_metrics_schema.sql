-- Flyway Migration V3: Event Outbox, Webhooks, Daily Metrics, and Audit Logs
-- UMIS (Universal Mailbox Integration Service)

CREATE TABLE IF NOT EXISTS event_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    event_version VARCHAR(16) NOT NULL DEFAULT '1.0',
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64) NOT NULL,
    subscription_id VARCHAR(255),
    event_type VARCHAR(128),
    mailbox_id UUID REFERENCES mailbox_connections(id) ON DELETE SET NULL,
    payload TEXT NOT NULL,
    headers TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSED',
    error_details VARCHAR(2000),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS daily_ingestion_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_date DATE NOT NULL,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    mailbox_id UUID REFERENCES mailbox_connections(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    emails_discovered INT NOT NULL DEFAULT 0,
    emails_picked INT NOT NULL DEFAULT 0,
    emails_ingested INT NOT NULL DEFAULT 0,
    emails_published INT NOT NULL DEFAULT 0,
    emails_parsed_success INT NOT NULL DEFAULT 0,
    emails_failed INT NOT NULL DEFAULT 0,
    duplicate_emails INT NOT NULL DEFAULT 0,
    total_attachment_bytes BIGINT NOT NULL DEFAULT 0,
    avg_ingestion_latency_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_metric_tenant_mailbox_date UNIQUE (metric_date, tenant_id, mailbox_id, provider)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(255),
    details VARCHAR(2000),
    ip_address VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_occurred ON event_outbox(status, occurred_at);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant ON event_outbox(tenant_id);
CREATE INDEX IF NOT EXISTS idx_webhook_prov_sub ON webhook_events(provider, subscription_id);
CREATE INDEX IF NOT EXISTS idx_daily_metrics_tenant_date ON daily_ingestion_metrics(tenant_id, metric_date DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_action ON audit_logs(tenant_id, action, created_at DESC);
