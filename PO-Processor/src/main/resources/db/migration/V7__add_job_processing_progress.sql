-- =========================================================================
-- PO Processor - Schema Migration (V7)
-- Adds persistent progress tracking columns to processing_jobs.
-- =========================================================================

ALTER TABLE po_processor.processing_jobs
    ADD COLUMN IF NOT EXISTS progress_percentage INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_processing_stage VARCHAR(50) NOT NULL DEFAULT 'EMAIL_RECEIVED',
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS processing_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS processing_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS progress_message VARCHAR(255) DEFAULT 'Email received';
