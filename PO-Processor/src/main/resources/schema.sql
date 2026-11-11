-- =========================================================================
-- PO Processor - Canonical Database Schema Definition
-- Single source of truth for the complete po_processor database schema.
-- Must be updated in sync with Flyway migrations.
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
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    email_subject VARCHAR(500),
    email_sender VARCHAR(255),
    email_sender_name VARCHAR(255),
    email_recipients TEXT,
    locked_by VARCHAR(100),
    locked_at TIMESTAMP WITH TIME ZONE,
    lock_expires_at TIMESTAMP WITH TIME ZONE,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    po_number VARCHAR(100),
    hospital_name VARCHAR(255),
    vendor_name VARCHAR(255),
    total_amount NUMERIC(15,2),
    currency VARCHAR(10),
    extraction_method VARCHAR(50),
    duplicate_of_id UUID,
    body_storage_ref VARCHAR(500),
    progress_percentage INT NOT NULL DEFAULT 0,
    current_processing_stage VARCHAR(50) NOT NULL DEFAULT 'EMAIL_RECEIVED',
    processing_started_at TIMESTAMP WITH TIME ZONE,
    processing_updated_at TIMESTAMP WITH TIME ZONE,
    processing_completed_at TIMESTAMP WITH TIME ZONE,
    progress_message VARCHAR(255) DEFAULT 'Email received',
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pj_tenant_status ON po_processor.processing_jobs (tenant_id, processing_status);
CREATE INDEX IF NOT EXISTS idx_pj_umis_email ON po_processor.processing_jobs (umis_email_id);
CREATE INDEX IF NOT EXISTS idx_pj_event_id ON po_processor.processing_jobs (event_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_pj_tenant_email ON po_processor.processing_jobs (tenant_id, umis_email_id);
CREATE INDEX IF NOT EXISTS idx_pj_email_subject ON po_processor.processing_jobs (email_subject);
CREATE INDEX IF NOT EXISTS idx_pj_email_sender ON po_processor.processing_jobs (email_sender);
CREATE INDEX IF NOT EXISTS idx_pj_worker_claim ON po_processor.processing_jobs (processing_status, lock_expires_at);
CREATE INDEX IF NOT EXISTS idx_pj_po_number ON po_processor.processing_jobs (tenant_id, po_number);

-- 2. Table: processing_job_attachments
CREATE TABLE IF NOT EXISTS po_processor.processing_job_attachments (
    id UUID PRIMARY KEY,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    storage_reference VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    file_hash VARCHAR(128),
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

-- 4. Table: hospitals (Customer Master)
CREATE TABLE IF NOT EXISTS po_processor.hospitals (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    hospital_code VARCHAR(100),
    tax_id VARCHAR(100),
    address TEXT,
    city VARCHAR(100),
    state_region VARCHAR(100),
    postal_code VARCHAR(50),
    country VARCHAR(100),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hospitals_tenant_name UNIQUE (tenant_id, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_hospitals_tenant_code ON po_processor.hospitals (tenant_id, hospital_code);

-- 5. Table: hospital_aliases (Name Variants)
CREATE TABLE IF NOT EXISTS po_processor.hospital_aliases (
    id UUID PRIMARY KEY,
    hospital_id UUID NOT NULL REFERENCES po_processor.hospitals(id) ON DELETE CASCADE,
    alias_name VARCHAR(255) NOT NULL,
    confidence NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hospital_alias UNIQUE (hospital_id, alias_name)
);

CREATE INDEX IF NOT EXISTS idx_hospital_aliases_name ON po_processor.hospital_aliases (alias_name);

-- 6. Table: extracted_purchase_orders (Canonical PO Header & Workflow State)
CREATE TABLE IF NOT EXISTS po_processor.extracted_purchase_orders (
    id UUID PRIMARY KEY,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    hospital_id UUID REFERENCES po_processor.hospitals(id),
    parent_po_id UUID REFERENCES po_processor.extracted_purchase_orders(id),
    revision_number INT NOT NULL DEFAULT 1,
    po_number VARCHAR(100) NOT NULL,
    po_title_description VARCHAR(500),
    po_date DATE,
    requested_delivery_date DATE,
    expected_delivery_date DATE,
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    subtotal NUMERIC(15,2),
    discount NUMERIC(15,2),
    freight_charges NUMERIC(15,2),
    tax_amount NUMERIC(15,2),
    tax_percentage NUMERIC(5,2),
    grand_total NUMERIC(15,2) NOT NULL,
    payment_terms VARCHAR(255),
    payment_method VARCHAR(100),
    delivery_instructions TEXT,
    incoterms VARCHAR(50),
    contract_reference_number VARCHAR(100),
    quotation_reference VARCHAR(100),
    workflow_status VARCHAR(50) NOT NULL DEFAULT 'READY_FOR_REVIEW',
    vendor_name VARCHAR(255),
    vendor_address TEXT,
    vendor_contact VARCHAR(255),
    vendor_email VARCHAR(255),
    vendor_phone VARCHAR(50),
    vendor_tax_id VARCHAR(100),
    vendor_account_number VARCHAR(100),
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP WITH TIME ZONE,
    verified_by VARCHAR(255),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_superseded BOOLEAN NOT NULL DEFAULT FALSE,
    superseded_by_id UUID REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE SET NULL,
    superseded_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_epo_tenant_po ON po_processor.extracted_purchase_orders (tenant_id, po_number);
CREATE INDEX IF NOT EXISTS idx_epo_tenant_hospital_status ON po_processor.extracted_purchase_orders (tenant_id, hospital_id, workflow_status);
CREATE INDEX IF NOT EXISTS idx_epo_job_id ON po_processor.extracted_purchase_orders (processing_job_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_epo_processing_job ON po_processor.extracted_purchase_orders (processing_job_id);
CREATE INDEX IF NOT EXISTS idx_epo_superseded ON po_processor.extracted_purchase_orders (tenant_id, hospital_id, is_approved, is_superseded);
CREATE UNIQUE INDEX IF NOT EXISTS uk_epo_tenant_po_revision ON po_processor.extracted_purchase_orders (tenant_id, po_number, revision_number);

-- 7. Table: extracted_po_line_items
CREATE TABLE IF NOT EXISTS po_processor.extracted_po_line_items (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    product_code VARCHAR(100),
    mfg_part_number VARCHAR(100),
    product_name VARCHAR(255),
    description TEXT,
    category VARCHAR(100),
    quantity NUMERIC(12,3) NOT NULL,
    unit VARCHAR(50),
    unit_price NUMERIC(15,2) NOT NULL,
    discount NUMERIC(15,2),
    tax_percentage NUMERIC(5,2),
    tax_amount NUMERIC(15,2),
    line_subtotal NUMERIC(15,2),
    line_total NUMERIC(15,2) NOT NULL,
    currency VARCHAR(10),
    batch_lot_reference VARCHAR(100),
    serial_number VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_epli_po_line ON po_processor.extracted_po_line_items (po_id, line_number);

-- 8. Table: po_patient_details (Sensitive Clinical Reference)
CREATE TABLE IF NOT EXISTS po_processor.po_patient_details (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    patient_name VARCHAR(255),
    patient_id_mrn VARCHAR(100),
    patient_ref_number VARCHAR(100),
    date_of_birth DATE,
    procedure_case_reference VARCHAR(255),
    surgeon_doctor_name VARCHAR(255),
    department_specialty VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_patient_details_po ON po_processor.po_patient_details (po_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_details_po ON po_processor.po_patient_details (po_id);

-- 9. Table: po_billing_details
CREATE TABLE IF NOT EXISTS po_processor.po_billing_details (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    billing_entity VARCHAR(255),
    billing_contact VARCHAR(255),
    billing_address TEXT,
    city VARCHAR(100),
    state_region VARCHAR(100),
    postal_code VARCHAR(50),
    country VARCHAR(100),
    tax_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_billing_details_po ON po_processor.po_billing_details (po_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_details_po ON po_processor.po_billing_details (po_id);

-- 10. Table: po_shipping_details
CREATE TABLE IF NOT EXISTS po_processor.po_shipping_details (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    ship_to_entity VARCHAR(255),
    hospital_location_name VARCHAR(255),
    shipping_contact VARCHAR(255),
    shipping_address TEXT,
    city VARCHAR(100),
    state_region VARCHAR(100),
    postal_code VARCHAR(50),
    country VARCHAR(100),
    delivery_instructions TEXT,
    requested_delivery_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shipping_details_po ON po_processor.po_shipping_details (po_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_shipping_details_po ON po_processor.po_shipping_details (po_id);

-- 11. Table: po_document_analyses (Attachment Metadata & OCR Diagnostics)
CREATE TABLE IF NOT EXISTS po_processor.po_document_analyses (
    id UUID PRIMARY KEY,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    processing_job_attachment_id UUID REFERENCES po_processor.processing_job_attachments(id) ON DELETE SET NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100),
    file_size BIGINT,
    file_hash VARCHAR(128),
    page_count INT DEFAULT 1,
    document_classification VARCHAR(50) NOT NULL,
    extraction_method VARCHAR(50) NOT NULL,
    extraction_engine VARCHAR(100),
    ocr_used BOOLEAN NOT NULL DEFAULT FALSE,
    processing_result VARCHAR(50) NOT NULL,
    error_details TEXT,
    extracted_text_preview TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_analysis_job ON po_processor.po_document_analyses (processing_job_id);

-- 12. Table: po_extraction_runs (AI Model Execution Telemetry)
CREATE TABLE IF NOT EXISTS po_processor.po_extraction_runs (
    id UUID PRIMARY KEY,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    model_name VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    input_tokens INT,
    output_tokens INT,
    execution_duration_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    raw_response_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_extraction_runs_job ON po_processor.po_extraction_runs (processing_job_id);

-- 13. Table: po_field_evidence (Provenance Snippets & Confidence)
CREATE TABLE IF NOT EXISTS po_processor.po_field_evidence (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    extracted_value TEXT,
    normalized_value TEXT,
    confidence_score NUMERIC(4,3),
    source_type VARCHAR(50) NOT NULL,
    source_attachment_id UUID REFERENCES po_processor.processing_job_attachments(id) ON DELETE SET NULL,
    source_file_name VARCHAR(255),
    source_page INT,
    evidence_text TEXT,
    is_inferred BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_field_evidence_po ON po_processor.po_field_evidence (po_id, field_name);

-- 14. Table: po_body_attachment_comparisons (Reconciliation Report)
CREATE TABLE IF NOT EXISTS po_processor.po_body_attachment_comparisons (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    comparison_status VARCHAR(50) NOT NULL,
    comparison_report JSONB NOT NULL,
    conflict_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comparisons_po ON po_processor.po_body_attachment_comparisons (po_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_comparison_po ON po_processor.po_body_attachment_comparisons (po_id);
CREATE INDEX IF NOT EXISTS idx_comparisons_job ON po_processor.po_body_attachment_comparisons (processing_job_id);

-- 15. Table: po_validation_results (Rule Checks & Balances)
CREATE TABLE IF NOT EXISTS po_processor.po_validation_results (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    rule_code VARCHAR(100) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    passed BOOLEAN NOT NULL,
    expected_value TEXT,
    actual_value TEXT,
    explanation TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_validations_po ON po_processor.po_validation_results (po_id);

-- 16. Table: po_duplicate_detections (Duplicate & Revision Links)
CREATE TABLE IF NOT EXISTS po_processor.po_duplicate_detections (
    id UUID PRIMARY KEY,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    original_po_id UUID REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE SET NULL,
    duplicate_type VARCHAR(50) NOT NULL,
    detection_reason TEXT NOT NULL,
    confidence_score NUMERIC(4,3),
    review_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_duplicate_job ON po_processor.po_duplicate_detections (processing_job_id);
CREATE INDEX IF NOT EXISTS idx_duplicate_original_po ON po_processor.po_duplicate_detections (original_po_id);

-- 17. Table: po_approval_history (Reviewer Actions)
CREATE TABLE IF NOT EXISTS po_processor.po_approval_history (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    action_by VARCHAR(255) NOT NULL,
    action_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments TEXT
);

CREATE INDEX IF NOT EXISTS idx_approval_history_po ON po_processor.po_approval_history (po_id, action_at);

-- 18. Table: po_field_edit_history (Human Modification Audit Trail)
CREATE TABLE IF NOT EXISTS po_processor.po_field_edit_history (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    original_value TEXT,
    new_value TEXT,
    edited_by VARCHAR(255) NOT NULL,
    edited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edit_reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_edit_history_po ON po_processor.po_field_edit_history (po_id, edited_at);
