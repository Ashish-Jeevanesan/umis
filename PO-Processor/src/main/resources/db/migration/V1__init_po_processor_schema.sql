-- =========================================================================
-- PO Processor - Schema & Initial DDL Migration (V1)
-- Isolated schema dedicated strictly to PO Processor microservice
-- =========================================================================

CREATE SCHEMA IF NOT EXISTS po_processor;

-- 1. Table: processing_jobs
CREATE TABLE IF NOT EXISTS po_processor.processing_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    umis_email_id UUID NOT NULL,
    mailbox_id UUID NOT NULL,
    event_id UUID NOT NULL,
    processing_status VARCHAR(50) NOT NULL,
    processing_attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pj_tenant_status ON po_processor.processing_jobs (tenant_id, processing_status);
CREATE INDEX IF NOT EXISTS idx_pj_umis_email ON po_processor.processing_jobs (umis_email_id);
CREATE INDEX IF NOT EXISTS idx_pj_event_id ON po_processor.processing_jobs (event_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pj_tenant_email ON po_processor.processing_jobs (tenant_id, umis_email_id);

-- 2. Table: processing_job_attachments
CREATE TABLE IF NOT EXISTS po_processor.processing_job_attachments (
    id UUID PRIMARY KEY,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    storage_reference VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    processing_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pja_job_id ON po_processor.processing_job_attachments (processing_job_id);

-- 3. Table: processed_events (Idempotency Tracking)
CREATE TABLE IF NOT EXISTS po_processor.processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pe_event_consumer UNIQUE (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_pe_event_id ON po_processor.processed_events (event_id);
