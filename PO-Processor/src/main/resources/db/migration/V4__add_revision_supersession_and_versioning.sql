-- =========================================================================
-- PO Processor - Schema Migration (V4)
-- Adds explicit revision supersession, optimistic locking (@Version),
-- and unique revision constraints
-- =========================================================================

-- 1. Add version column to processing_jobs for optimistic locking
ALTER TABLE po_processor.processing_jobs
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. Add version and supersession columns to extracted_purchase_orders
ALTER TABLE po_processor.extracted_purchase_orders
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_superseded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS superseded_by_id UUID REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMP WITH TIME ZONE;

-- 3. Index for querying active non-superseded approved purchase orders
CREATE INDEX IF NOT EXISTS idx_epo_superseded
    ON po_processor.extracted_purchase_orders (tenant_id, hospital_id, is_approved, is_superseded);

-- 4. Unique constraint ensuring no duplicate revision number for a PO within a tenant
CREATE UNIQUE INDEX IF NOT EXISTS uk_epo_tenant_po_revision
    ON po_processor.extracted_purchase_orders (tenant_id, po_number, revision_number);
