# PO Processor — AI-Powered Purchase Order Intelligence Engine

The **PO Processor** is an independently deployable Spring Boot microservice that receives email ingestion events from the **Universal Mailbox Integration Service (UMIS)** over RabbitMQ.

It retrieves the complete email body and attachments from durable storage, understands document content using Google Gemini API and Apache PDFBox with OCR/Vision fallback, extracts structured purchase order data into normalized relational tables, performs side-by-side reconciliation between email bodies and attachments, detects duplicates and revisions, maintains multi-currency hospital purchasing histories, and provides an audited human review, verification, and approval workflow.

The core UMIS microservice, its RabbitMQ event contracts, and the PostgreSQL `public` schema remain completely unmodified and independent.

---

## 1. System Architecture & Processing Workflow

```
[UMIS Email Ingestion]
        │
        ▼ (RabbitMQ: umis.events.email.ingested)
[PoEmailIngestedConsumer] ──(Persist QUEUED job & manual ACK)──► RabbitMQ
        │
        ▼ (Database-Backed Worker & Lease Acquisition)
[PoJobWorkerService]
   ├── 1. Storage Retrieval (Email Body Text/HTML + Attachments)
   ├── 2. Document Analysis (Apache PDFBox digital text extraction + Gemini Vision fallback)
   ├── 3. Gemini Structured Extraction (PO Header, Line Items, Patient, Billing, Shipping)
   ├── 4. Duplicate & Revision Detection (Exact hash match vs. versioned parent_po_id)
   ├── 5. Hospital Master Normalization & Alias Matching
   ├── 6. Body vs. Attachment Side-by-Side Comparison
   ├── 7. Validation Engine (BigDecimal financial formulas & mandatory fields)
   └── 8. Database Persistence (po_processor schema)
        │
        ▼
   [State: READY_FOR_REVIEW / NEEDS_REVIEW / DUPLICATE]
        │
        ▼
   [Human Review Workflow: Edit -> Verify -> Approve]
```

---

## 2. Decoupled State Machines

The system strictly decouples technical job execution from business approval:

### Technical Processing Status (`JobProcessingStatus`)
- `QUEUED`: Durable job saved in PostgreSQL, waiting for worker lease.
- `PROCESSING`: Worker holds active lease lock and is executing extraction.
- `READY_FOR_REVIEW`: Extraction succeeded, ready for human review.
- `DUPLICATE`: Document identified as a duplicate submission of an existing PO.
- `FAILED`: Unrecoverable extraction failure; diagnostics stored for retry.
- *(Legacy: `PENDING`, `READY_FOR_EXTRACTION`, `COMPLETED`).*

### Business PO Workflow Status (`PoWorkflowStatus`)
- `DRAFT`: Initial extracted PO data.
- `READY_FOR_REVIEW`: Extraction and automated rule checks passed.
- `NEEDS_REVIEW`: Conflicts detected (e.g., body vs attachment mismatch, missing fields).
- `EDITED`: Human reviewer edited fields; invalidates prior verification.
- `VERIFIED`: Reviewer verified mandatory fields and financial equations. Does NOT approve.
- `APPROVED`: Final human approval. Makes PO read-only and updates hospital purchasing totals.

---

## 3. Database Schema (`po_processor`)

All PO data is stored in isolated relational tables in the `po_processor` PostgreSQL schema:

- `processing_jobs`: Job execution lifecycle, worker lease locks, retry metadata, summary cache.
- `processing_job_attachments`: Attachment metadata and SHA-256 hashes.
- `processed_events`: Ingestion idempotency registry.
- `hospitals`: Canonical customer / hospital master accounts.
- `hospital_aliases`: Alternative hospital name variants and confidence scores.
- `extracted_purchase_orders`: Canonical PO headers, financials, approval state, revision pointers.
- `extracted_po_line_items`: Line items (SKU, description, qty, unit price, tax, totals).
- `po_patient_details`: Sensitive clinical and patient references (isolated for privacy).
- `po_billing_details`: Invoicing entities, addresses, and tax identifiers.
- `po_shipping_details`: Logistics delivery instructions and shipping addresses.
- `po_document_analyses`: File classification, extraction method used, page count, OCR flag.
- `po_extraction_runs`: AI model telemetry (tokens, duration, status, raw response).
- `po_field_evidence`: Field-level provenance snippets and confidence scores.
- `po_body_attachment_comparisons`: Side-by-side reconciliation report and conflict counts.
- `po_validation_results`: Financial and structural rule verification outcomes.
- `po_duplicate_detections`: Duplicate classifications, detection reasons, and original PO links.
- `po_approval_history`: Workflow actions (`VERIFIED`, `APPROVED`, etc.) with user and timestamp.
- `po_field_edit_history`: Immutable human modification audit trail.

**Canonical Schema**: The complete single source of truth is maintained at:
`PO-Processor/src/main/resources/schema.sql`.

---

## 4. Configuration & Environment Variables

Configure these settings in your `.env` or system environment:

```properties
# Server Port
SERVER_PORT=8085

# Supabase PostgreSQL Connection
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database>
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<password>

# Google Gemini Document Intelligence Settings
GEMINI_API_KEY=<your-google-gemini-api-key>
GEMINI_MODEL=gemini-1.5-flash
GEMINI_ENABLED=true
GEMINI_TIMEOUT_SECONDS=60

# Background Worker Settings
PO_WORKER_POLL_INTERVAL_MS=3000
PO_WORKER_MAX_RETRIES=3

# Storage (Read-Only access to UMIS storage directory)
PO_STORAGE_TYPE=LOCAL
PO_STORAGE_LOCAL_BASE_PATH=../backend/storage
```

---

## 5. Running the Application Locally

### Prerequisites
- Java 21 (`JDK 21`)
- Maven 3.9+
- RabbitMQ running on `localhost:5672`
- UMIS backend and frontend running

### Run PO Processor Backend
```powershell
cd PO-Processor
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
mvn spring-boot:run
```

### Run Tests
```powershell
cd PO-Processor
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
mvn test
```

### Accessing the Integrated Frontend
Open browser at `http://localhost:3000/po` for the dashboard and `http://localhost:3000/po/jobs/:id` for individual PO review.
