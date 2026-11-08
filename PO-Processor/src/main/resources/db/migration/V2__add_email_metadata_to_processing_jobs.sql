-- =========================================================================
-- PO Processor - Schema Migration (V2)
-- Add email metadata and attachment file hash for dashboard display
-- =========================================================================

ALTER TABLE po_processor.processing_jobs
    ADD COLUMN IF NOT EXISTS email_subject VARCHAR(500),
    ADD COLUMN IF NOT EXISTS email_sender VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_sender_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_recipients TEXT;

CREATE INDEX IF NOT EXISTS idx_pj_email_subject ON po_processor.processing_jobs (email_subject);
CREATE INDEX IF NOT EXISTS idx_pj_email_sender ON po_processor.processing_jobs (email_sender);

ALTER TABLE po_processor.processing_job_attachments
    ADD COLUMN IF NOT EXISTS file_hash VARCHAR(128);
