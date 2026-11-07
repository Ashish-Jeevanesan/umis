-- Flyway Migration V2: Email Ingestion, Attachments, and Consumer Schema
-- UMIS (Universal Mailbox Integration Service)

CREATE TABLE IF NOT EXISTS email_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    mailbox_id UUID NOT NULL REFERENCES mailbox_connections(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    provider_message_id VARCHAR(255) NOT NULL,
    thread_id VARCHAR(255),
    internet_message_id VARCHAR(255),
    sender VARCHAR(255) NOT NULL,
    sender_name VARCHAR(255),
    subject VARCHAR(1024),
    snippet VARCHAR(1024),
    body_text_storage_ref VARCHAR(512),
    body_html_storage_ref VARCHAR(512),
    body_html_available BOOLEAN NOT NULL DEFAULT FALSE,
    content_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ingestion_status VARCHAR(32) NOT NULL DEFAULT 'INGESTED',
    ingestion_error_details VARCHAR(2000),
    has_attachments BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mailbox_provider_msg UNIQUE (mailbox_id, provider_message_id)
);

CREATE TABLE IF NOT EXISTS email_recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id UUID NOT NULL REFERENCES email_messages(id) ON DELETE CASCADE,
    recipient_type VARCHAR(16) NOT NULL, -- TO, CC, BCC
    address VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS email_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id UUID NOT NULL REFERENCES email_messages(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    storage_reference VARCHAR(512) NOT NULL,
    content_id VARCHAR(255),
    is_inline BOOLEAN NOT NULL DEFAULT FALSE,
    scan_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    mailbox_id UUID NOT NULL REFERENCES mailbox_connections(id) ON DELETE CASCADE,
    trigger_type VARCHAR(32) NOT NULL, -- POLLING, WEBHOOK, MANUAL_SYNC, HISTORICAL
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    items_discovered INT NOT NULL DEFAULT 0,
    items_ingested INT NOT NULL DEFAULT 0,
    items_skipped INT NOT NULL DEFAULT 0,
    items_failed INT NOT NULL DEFAULT 0,
    error_summary VARCHAR(2000),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS consumer_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE, -- NULL means authorized global consumer
    consumer_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(512),
    subscribed_events VARCHAR(512) NOT NULL DEFAULT 'EMAIL_INGESTED',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_consumer_tenant_code UNIQUE (tenant_id, consumer_code)
);

CREATE TABLE IF NOT EXISTS email_processing_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_id UUID NOT NULL REFERENCES email_messages(id) ON DELETE CASCADE,
    consumer_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reference_id VARCHAR(255),
    message VARCHAR(2000),
    processed_at TIMESTAMP WITH TIME ZONE,
    acknowledgement_idempotency_key VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_email_consumer UNIQUE (email_id, consumer_code)
);

CREATE INDEX IF NOT EXISTS idx_email_tenant_received ON email_messages(tenant_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_mailbox_received ON email_messages(mailbox_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_ingestion_status ON email_messages(ingestion_status);
CREATE INDEX IF NOT EXISTS idx_email_hash ON email_messages(tenant_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_email_recipients_email ON email_recipients(email_id);
CREATE INDEX IF NOT EXISTS idx_email_attachments_email ON email_attachments(email_id);
CREATE INDEX IF NOT EXISTS idx_email_proc_status_consumer ON email_processing_status(consumer_code, status);
