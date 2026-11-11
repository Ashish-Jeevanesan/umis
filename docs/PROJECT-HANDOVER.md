# UMIS + PO Processor — Final Project Handover Documentation

**Document Version:** 1.0.0 (Production-Ready Functional Baseline)  
**Date:** Monday, November 9, 2026  
**Target Systems:** UMIS (Port 8080) & PO Processor (Port 8085), Frontend (Port 3000)  
**Database:** PostgreSQL 16 (`po_processor` schema isolated from `public`)  
**Message Broker:** RabbitMQ 3.13  

---

## 1. System Architecture Overview

The system is composed of two independent microservices communicating asynchronously via RabbitMQ:

```
[Inbound Email Sources] (M365, Gmail, IMAP)
             │
             ▼
[UMIS Ingestion Service] (Port 8080, public schema)
   ├── Normalizes MIME & extracts attachments to ./storage
   └── Publishes EMAIL_INGESTED event to RabbitMQ
             │
             ▼ (Exchange: umis.events, Routing Key: umis.email.ingested)
[RabbitMQ Message Broker] (Port 5672)
   ├── Queue: po-processor.queue.email-ingested
   └── DLQ:   po-processor.queue.dlq
             │
             ▼
[PO Processor Microservice] (Port 8085, schema: po_processor)
   ├── 1. PoEmailIngestedConsumer (Idempotency deduplication)
   ├── 2. BoundedAttachmentStreamReader (25MB file, 100MB job limits)
   ├── 3. Document Extractors (PDFBox <=50 pages, POI Docx ZipSecure, Image <=4096px)
   ├── 4. GeminiDocumentIntelligenceProvider (gemini-3.8-flash, x-goog-api-key)
   ├── 5. BodyAttachmentComparator (Side-by-side reconciliation & conflict detection)
   ├── 6. DuplicateDetectionService (PostgreSQL transaction advisory locks)
   ├── 7. HospitalMasterService (Normalized names & multi-currency history)
   ├── 8. PoValidationService (Line item sums, 0.02 rounding tolerance, balance check)
   └── 9. PoWorkflowService (JPA @Version, revision supersession, approval flow)
             │
             ▼
[PostgreSQL Database] (Port 5432)
   ├── schema: public (UMIS mailboxes, emails, attachments, audit events)
   └── schema: po_processor (15 relational PO intelligence tables)
             │
             ▼
[Single React 18 Frontend] (Port 3000, Vite 5)
   ├── /umis (Mailbox health, metrics, email explorer)
   ├── /po (PO dashboard, KPI cards, filterable jobs table)
   └── /po/jobs/:id (7-tab review interface: Overview, Details, Lines, Compare, History, Docs, Audit)
```

---

## 2. Implemented Features Directory

### Phase 1–7 Core Intelligence Pipeline
* **Asynchronous Queue Ingestion:** Listens to `EMAIL_INGESTED` events; claims jobs with atomic database leasing.
* **Storage Abstraction:** Supports local filesystem and cloud object stores with path-traversal prevention.
* **Document Intelligence Engine:** Prompt-injection shielded prompting (`<untrusted_document_content>`); structured JSON parsing.
* **Side-by-Side Reconciliation:** Compares email text with extracted PO attributes; detects conflicting totals/dates.
* **Hospital Master & BI:** Dynamic currency totals grouped by `(tenant_id, hospital_id, currency)`.
* **Mathematical Validation Engine:** Independent Java `BigDecimal` balance equation checking.
* **Human-in-the-Loop Workflow:** Audited field edits, verification invalidation, and approval finalization.

### Security, Concurrency & Resilience Remediation
* **Phase A (Auth & RBAC):** Stateless HMAC-SHA256 JWT validation, role hierarchy (`PO_VIEWER` to `PO_ADMIN`), tenant scoping (`findByIdAndTenantId`), removal of trusted identity headers.
* **Phase B (Financial Integrity & Concurrency):** Revision supersession (`is_superseded`); exclusion from purchasing totals; PostgreSQL transaction advisory locking on `(tenant_id, po_number)` + unique constraint `uk_epo_tenant_po_revision`; JPA `@Version` optimistic locking returning HTTP 409 Conflict.
* **Phase C (Attachment Security & Memory Limits):** 8KB chunk bounded streaming with 25MB file and 100MB cumulative job limits; PDFBox 50-page ceiling and encryption catch; Apache POI `ZipSecureFile.setMinInflateRatio(0.01)`; image dimension cap (4096px); Gemini `maxOutputTokens: 8192` with `GeminiResponseTruncatedException` on `MAX_TOKENS`.
* **Phase D (Live AI Integration):** Dynamic model discovery upgrading default model to `gemini-3.8-flash`; real HTTPS live benchmark across 30 layout categories (85.33% zero-shot accuracy, $9.6\text{s}$ latency, $\$0.11$ cost per 1,000 POs).

---

## 3. Database Schema & Flyway Migration Inventory

All PO processing tables reside strictly inside the `po_processor` PostgreSQL schema.

| Version | Migration Script | Description |
| :---: | :--- | :--- |
| **V1** | `V1__init_po_processor_schema.sql` | Creates schema `po_processor` and foundational `processing_jobs`, `processing_job_attachments`. |
| **V2** | `V2__add_worker_lease_and_retry_columns.sql` | Adds distributed worker leasing columns (`locked_by`, `lock_expires_at`, `retry_count`, `next_retry_at`). |
| **V3** | `V3__init_po_intelligence_schema.sql` | Defines 15 relational tables: hospitals, aliases, extracted POs, line items, patient/billing/shipping details, analyses, extraction runs, evidence, comparisons, validations, duplicates, approval history, edit history. |
| **V4** | `V4__add_revision_supersession_and_versioning.sql` | Adds JPA `@Version` columns, `is_superseded`, `superseded_by_id`, `superseded_at`, index `idx_epo_superseded`, and unique constraint `uk_epo_tenant_po_revision`. |

*Canonical DDL synchronization is maintained in `PO-Processor/src/main/resources/schema.sql`.*

---

## 4. API Endpoint Reference & Role Requirements

Base URL: `http://localhost:8085/api/v1`  
Authentication: HTTP Header `Authorization: Bearer <HMAC-SHA256-JWT>`  

| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/processing-jobs` | `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN` | List paginated jobs with status/search filtering |
| `GET` | `/processing-jobs/{id}` | `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN` | Get job header summary |
| `GET` | `/processing-jobs/{id}/full-details`| `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN` | Get complete 7-tab review graph |
| `POST` | `/processing-jobs/{id}/fields` | `PO_REVIEWER`, `PO_ADMIN` | Edit extracted PO field (invalidates verification) |
| `POST` | `/processing-jobs/{id}/verify` | `PO_VERIFIER`, `PO_ADMIN` | Verify financial equations and completeness |
| `POST` | `/processing-jobs/{id}/approve`| `PO_APPROVER`, `PO_ADMIN` | Approve PO (supersedes previous revisions) |
| `POST` | `/processing-jobs/{id}/reject` | `PO_APPROVER`, `PO_ADMIN` | Reject PO revision |
| `POST` | `/processing-jobs/{id}/re-extract`| `PO_ADMIN` | Trigger full AI re-extraction |
| `GET` | `/hospitals/{id}/history` | `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN` | Get multi-currency hospital lifetime totals |

---

## 5. RabbitMQ Configuration

* **Exchange:** `umis.events` (Topic Exchange, Durable)
* **Ingestion Queue:** `po-processor.queue.email-ingested` (Durable, bounded prefetch: 5)
* **Routing Key:** `umis.email.ingested`
* **Dead Letter Queue (DLQ):** `po-processor.queue.dlq` (Routing Key: `po-processor.dlq`)
* **Dead Letter Strategy:** Poison messages failing JSON deserialization or unhandled worker faults route to DLQ after 3 retry attempts.

---

## 6. Windows Local Development & Startup Instructions

### Prerequisites
1. **Java JDK 21:** Configured in `JAVA_HOME`.
2. **Node.js 20+ & npm:** For frontend Vite server.
3. **PostgreSQL 16:** Running on port 5432 with database `umis_db`.
4. **RabbitMQ 3.13:** Running on port 5672 with management UI on 15672.

### Step-by-Step Launch
1. **Start Core Infrastructure:**
   ```powershell
   # From repository root
   .\start-services.ps1
   ```
2. **Start Backend PO Processor (Port 8085):**
   ```powershell
   cd PO-Processor
   .\run.ps1
   ```
3. **Start React Frontend (Port 3000):**
   ```powershell
   cd frontend
   npm install
   npm run dev
   ```
4. Access applications:
   * Frontend: `http://localhost:3000/po`
   * PO Processor Health: `http://localhost:8085/actuator/health`
   * RabbitMQ Management: `http://localhost:15672` (guest / guest)

---

## 7. Required Environment Variables

All variables are environment-injected with safe fallbacks:

```bash
# PostgreSQL Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/umis_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_postgres_password_here

# RabbitMQ Configuration
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest

# JWT Authentication (UMIS Shared Secret)
# ROTATION MANDATE: Rotate this signing secret in production vaults before go-live!
PO_JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# Google Gemini AI Integration
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-3.8-flash
GEMINI_ENABLED=true
GEMINI_TIMEOUT_SECONDS=60
GEMINI_MAX_OUTPUT_TOKENS=8192
GEMINI_MAX_RETRIES=3

# Attachment Security & Memory Protection
PO_MAX_ATTACHMENT_SIZE_BYTES=26214400      # 25 MB
PO_MAX_TOTAL_ATTACHMENT_BYTES=104857600    # 100 MB
PO_MAX_PDF_PAGES=50
PO_MAX_DOCX_CHARS=500000
PO_MAX_IMAGE_DIMENSION=4096

# Worker Leasing & Retry
PO_WORKER_POLL_INTERVAL_MS=3000
PO_WORKER_MAX_RETRIES=3
PO_WORKFLOW_ALLOW_SELF_APPROVAL=false
```

---

## 8. Test Suite Summary & Reports

* **Total Backend Tests:** 67 tests passing (0 failures, 0 errors).
* **Test Command:**
  ```powershell
  $env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"; $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH; Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue; mvn test
  ```
* **Documentation & Audit Artifacts:**
  * Markdown Test Report: [`docs/COMPREHENSIVE-TEST-REPORT.md`](COMPREHENSIVE-TEST-REPORT.md)
  * HTML Print Layout: [`docs/COMPREHENSIVE-TEST-REPORT.html`](COMPREHENSIVE-TEST-REPORT.html)
  * Compiled PDF Report: [`docs/COMPREHENSIVE-TEST-REPORT.pdf`](COMPREHENSIVE-TEST-REPORT.pdf)

---

## 9. Backup, Recovery & Migration Guidelines

1. **Database Backup:**
   ```bash
   pg_dump -h localhost -U postgres -d umis_db -n po_processor -F c -b -v -f po_processor_backup.dump
   ```
2. **Database Restore:**
   ```bash
   pg_restore -h localhost -U postgres -d umis_db --clean --if-exists po_processor_backup.dump
   ```
3. **Flyway Migrations:** Handled automatically on Spring Boot application startup via `spring.flyway.enabled=true`. Never rewrite migrations V1 through V4.

---

## 10. Security Audit Sign-Off & Outstanding Actions

1. **API Key Decoupling:** Verified that `GEMINI_API_KEY` is not committed or hardcoded in any Git-tracked file.
2. **JWT Signing Secret Rotation:** The development signing key `404E6352...` must be rotated in production deployment pipelines and injected via KMS or cloud secrets manager.
3. **Multi-Page Scanned Documents:** If processing high volumes of multi-page purely physical paper scans without digital text, expand `AttachmentProcessingService` to rasterize pages 1 through 5.
