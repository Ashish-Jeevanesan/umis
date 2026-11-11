-- =========================================================================
-- PO Processor - Schema Migration (V3)
-- Adds AI-Powered PO Intelligence, Document Analyses, Line Items,
-- Hospitals, Comparison, Validation, Duplicate Detection, and Audit Trails
-- =========================================================================

-- 1. Enhance processing_jobs with leasing, retry, and PO summary fields
ALTER TABLE po_processor.processing_jobs
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS po_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS hospital_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS vendor_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS total_amount NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS extraction_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS duplicate_of_id UUID;

CREATE INDEX IF NOT EXISTS idx_pj_worker_claim
    ON po_processor.processing_jobs (processing_status, lock_expires_at);

CREATE INDEX IF NOT EXISTS idx_pj_po_number
    ON po_processor.processing_jobs (tenant_id, po_number);

-- 2. Table: hospitals (Customer Master)
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

CREATE INDEX IF NOT EXISTS idx_hospitals_tenant_code
    ON po_processor.hospitals (tenant_id, hospital_code);

-- 3. Table: hospital_aliases (Name Variants)
CREATE TABLE IF NOT EXISTS po_processor.hospital_aliases (
    id UUID PRIMARY KEY,
    hospital_id UUID NOT NULL REFERENCES po_processor.hospitals(id) ON DELETE CASCADE,
    alias_name VARCHAR(255) NOT NULL,
    confidence NUMERIC(3,2) NOT NULL DEFAULT 1.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hospital_alias UNIQUE (hospital_id, alias_name)
);

CREATE INDEX IF NOT EXISTS idx_hospital_aliases_name
    ON po_processor.hospital_aliases (alias_name);

-- 4. Table: extracted_purchase_orders (Canonical PO Header & Workflow State)
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
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_epo_tenant_po
    ON po_processor.extracted_purchase_orders (tenant_id, po_number);

CREATE INDEX IF NOT EXISTS idx_epo_tenant_hospital_status
    ON po_processor.extracted_purchase_orders (tenant_id, hospital_id, workflow_status);

CREATE INDEX IF NOT EXISTS idx_epo_job_id
    ON po_processor.extracted_purchase_orders (processing_job_id);

-- 5. Table: extracted_po_line_items
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

CREATE INDEX IF NOT EXISTS idx_epli_po_line
    ON po_processor.extracted_po_line_items (po_id, line_number);

-- 6. Table: po_patient_details (Sensitive Clinical Reference)
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

CREATE INDEX IF NOT EXISTS idx_patient_details_po
    ON po_processor.po_patient_details (po_id);

-- 7. Table: po_billing_details
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

CREATE INDEX IF NOT EXISTS idx_billing_details_po
    ON po_processor.po_billing_details (po_id);

-- 8. Table: po_shipping_details
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

CREATE INDEX IF NOT EXISTS idx_shipping_details_po
    ON po_processor.po_shipping_details (po_id);

-- 9. Table: po_document_analyses (Attachment Metadata & OCR Diagnostics)
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

CREATE INDEX IF NOT EXISTS idx_doc_analysis_job
    ON po_processor.po_document_analyses (processing_job_id);

-- 10. Table: po_extraction_runs (AI Model Execution Telemetry)
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

CREATE INDEX IF NOT EXISTS idx_extraction_runs_job
    ON po_processor.po_extraction_runs (processing_job_id);

-- 11. Table: po_field_evidence (Provenance Snippets & Confidence)
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

CREATE INDEX IF NOT EXISTS idx_field_evidence_po
    ON po_processor.po_field_evidence (po_id, field_name);

-- 12. Table: po_body_attachment_comparisons (Reconciliation Report)
CREATE TABLE IF NOT EXISTS po_processor.po_body_attachment_comparisons (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    processing_job_id UUID NOT NULL REFERENCES po_processor.processing_jobs(id) ON DELETE CASCADE,
    comparison_status VARCHAR(50) NOT NULL,
    comparison_report JSONB NOT NULL,
    conflict_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comparisons_po
    ON po_processor.po_body_attachment_comparisons (po_id);

CREATE INDEX IF NOT EXISTS idx_comparisons_job
    ON po_processor.po_body_attachment_comparisons (processing_job_id);

-- 13. Table: po_validation_results (Rule Checks & Balances)
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

CREATE INDEX IF NOT EXISTS idx_validations_po
    ON po_processor.po_validation_results (po_id);

-- 14. Table: po_duplicate_detections (Duplicate & Revision Links)
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

CREATE INDEX IF NOT EXISTS idx_duplicate_job
    ON po_processor.po_duplicate_detections (processing_job_id);

CREATE INDEX IF NOT EXISTS idx_duplicate_original_po
    ON po_processor.po_duplicate_detections (original_po_id);

-- 15. Table: po_approval_history (Reviewer Actions)
CREATE TABLE IF NOT EXISTS po_processor.po_approval_history (
    id UUID PRIMARY KEY,
    po_id UUID NOT NULL REFERENCES po_processor.extracted_purchase_orders(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    action_by VARCHAR(255) NOT NULL,
    action_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comments TEXT
);

CREATE INDEX IF NOT EXISTS idx_approval_history_po
    ON po_processor.po_approval_history (po_id, action_at);

-- 16. Table: po_field_edit_history (Human Modification Audit Trail)
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

CREATE INDEX IF NOT EXISTS idx_edit_history_po
    ON po_processor.po_field_edit_history (po_id, edited_at);
