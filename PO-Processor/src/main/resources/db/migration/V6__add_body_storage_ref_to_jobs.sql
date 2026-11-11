-- =========================================================================
-- PO Processor - Schema Migration (V6)
-- Adds body_storage_ref to processing_jobs to preserve email body storage pointers
-- for AI extraction and side-by-side reconciliation.
-- =========================================================================

ALTER TABLE po_processor.processing_jobs
    ADD COLUMN IF NOT EXISTS body_storage_ref VARCHAR(500);
