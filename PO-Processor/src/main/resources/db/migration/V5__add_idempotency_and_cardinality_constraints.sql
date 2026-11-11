-- =========================================================================
-- PO Processor - Schema Migration (V5)
-- Enforces strict 1-to-1 cardinality and idempotency constraints across
-- processing jobs, extracted purchase orders, details, and events.
-- =========================================================================

-- 1. Safely prune any historical duplicate PO attempts before creating unique index
DO $$
DECLARE
    dup_row RECORD;
BEGIN
    FOR dup_row IN
        SELECT id FROM po_processor.extracted_purchase_orders
        WHERE id NOT IN (
            SELECT id FROM (
                SELECT DISTINCT ON (processing_job_id) id
                FROM po_processor.extracted_purchase_orders
                ORDER BY processing_job_id, created_at DESC
            ) latest_records
        )
    LOOP
        UPDATE po_processor.extracted_purchase_orders SET parent_po_id = NULL WHERE parent_po_id = dup_row.id;
        DELETE FROM po_processor.extracted_po_line_items WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_patient_details WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_billing_details WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_shipping_details WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_body_attachment_comparisons WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_field_evidence WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_validation_results WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_approval_history WHERE po_id = dup_row.id;
        DELETE FROM po_processor.po_field_edit_history WHERE po_id = dup_row.id;
        DELETE FROM po_processor.extracted_purchase_orders WHERE id = dup_row.id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_epo_processing_job
    ON po_processor.extracted_purchase_orders (processing_job_id);

-- 2. Prune duplicates and create unique constraint on po_patient_details
DELETE FROM po_processor.po_patient_details
WHERE id NOT IN (
    SELECT id FROM (
        SELECT DISTINCT ON (po_id) id
        FROM po_processor.po_patient_details
        ORDER BY po_id, created_at DESC
    ) latest_pat
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_details_po
    ON po_processor.po_patient_details (po_id);

-- 3. Prune duplicates and create unique constraint on po_billing_details
DELETE FROM po_processor.po_billing_details
WHERE id NOT IN (
    SELECT id FROM (
        SELECT DISTINCT ON (po_id) id
        FROM po_processor.po_billing_details
        ORDER BY po_id, created_at DESC
    ) latest_bil
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_details_po
    ON po_processor.po_billing_details (po_id);

-- 4. Prune duplicates and create unique constraint on po_shipping_details
DELETE FROM po_processor.po_shipping_details
WHERE id NOT IN (
    SELECT id FROM (
        SELECT DISTINCT ON (po_id) id
        FROM po_processor.po_shipping_details
        ORDER BY po_id, created_at DESC
    ) latest_shp
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_shipping_details_po
    ON po_processor.po_shipping_details (po_id);

-- 5. Prune duplicates and create unique constraint on po_body_attachment_comparisons
DELETE FROM po_processor.po_body_attachment_comparisons
WHERE id NOT IN (
    SELECT id FROM (
        SELECT DISTINCT ON (po_id) id
        FROM po_processor.po_body_attachment_comparisons
        ORDER BY po_id, created_at DESC
    ) latest_cmp
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_comparison_po
    ON po_processor.po_body_attachment_comparisons (po_id);

-- 6. Ensure event idempotency: exactly one record per event per consumer
CREATE UNIQUE INDEX IF NOT EXISTS uk_pe_event_consumer
    ON po_processor.processed_events (event_id, consumer_name);

-- 7. Ensure at most one processing job per tenant and email
CREATE UNIQUE INDEX IF NOT EXISTS uk_pj_tenant_email
    ON po_processor.processing_jobs (tenant_id, umis_email_id);
