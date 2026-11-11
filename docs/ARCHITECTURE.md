# UMIS + PO Processor — Complete System Architecture Specification

## 1. High-Level Architectural Topology

The system comprises two autonomous, decoupled microservices integrated via asynchronous event distribution:

```
[Inbound Email Sources] (M365 Graph, Gmail API, IMAP)
             │
             ▼
+-------------------------------------------------------------+
| UMIS Core Service (Java 21, Spring Boot 3.3.4, Port 8080)   |
| - Mailbox polling & OAuth2 token refresh                    |
| - Content deduplication (SHA-256)                           |
| - Binary attachment offload to ./storage (S3 compatible)    |
| - Transactional Outbox pattern                              |
| - Schema: public                                            |
+------------------------------+------------------------------+
                               │
                               ▼ (EMAIL_INGESTED v1.0)
+-------------------------------------------------------------+
| RabbitMQ Message Broker (Port 5672)                         |
| - Topic Exchange: umis.events                               |
| - Ingestion Queue: po-processor.queue.email-ingested        |
| - Dead-Letter Queue: po-processor.queue.dlq                 |
+------------------------------+------------------------------+
                               │
                               ▼
+-------------------------------------------------------------+
| PO Processor Service (Java 21, Spring Boot 3.3.4, Port 8085)|
| - Consumer deduplication & durable DB worker leasing        |
| - Bounded attachment streaming (25MB file, 100MB job limits)|
| - PDFBox (<=50 pages) & Apache POI ZipSecureFile parser     |
| - Google Gemini 3.8 Flash Document Intelligence             |
| - Side-by-side reconciliation & conflict detection          |
| - PostgreSQL transaction advisory locks (CON-01)            |
| - JPA @Version optimistic locking (CON-02)                  |
| - Revision supersession & lifetime financial totals (FIN-01)|
| - Schema: po_processor                                      |
+------------------------------+------------------------------+
                               │
                               ▼
+-------------------------------------------------------------+
| Unified React 18 + Vite Frontend (Port 3000)                |
| - /umis: Ingestion health, mailbox explorer, system metrics |
| - /po: PO dashboard, KPI cards, filterable jobs table       |
| - /po/jobs/:id: 7-tab human-in-the-loop review workflow     |
+-------------------------------------------------------------+
```

---

## 2. Component Architecture & Data Flow

```
[RabbitMQ: EMAIL_INGESTED]
           │
           ▼
[PoEmailIngestedConsumer] ── Deduplicates against processing_jobs (umis_email_id)
           │
           ▼ (Job created with status QUEUED)
[PoJobWorkerService] ────── Scheduled polling claims leases with atomic DB lock
           │
           ▼ (Read attachments from StorageReader)
[BoundedAttachmentStreamReader]
   ├── Enforces 25MB file ceiling and 100MB job ceiling
   └── Computes single-pass SHA-256 hash
           │
           ▼
[Document Extractors]
   ├── PdfBoxDocumentExtractor (<=50 pages, encryption catch, page 0 rasterization)
   ├── DocxDocumentExtractor (Apache POI ZipSecureFile min inflate ratio 0.01)
   └── ImageDocumentValidator (Header-only dimension check <=4096px)
           │
           ▼
[GeminiPromptBuilder] ──── Prompt injection defense (<untrusted_document_content>)
           │
           ▼
[GeminiDocumentIntelligenceProvider]
   ├── Google Generative Language v1beta REST API (gemini-3.8-flash)
   ├── Authentication: x-goog-api-key header
   ├── GenerationConfig: maxOutputTokens: 8192, temperature: 0.1, response_mime_type: application/json
   └── Resilience: Bounded exponential backoff + jitter for 429/5xx, fail-fast on 401/403
           │
           ▼
[GeminiResponseParser]
   ├── Detects finishReason == "MAX_TOKENS" -> GeminiResponseTruncatedException
   └── Extracts token telemetry from usageMetadata (promptTokenCount, candidatesTokenCount)
           │
           ▼
[DuplicateDetectionService]
   ├── Acquires PostgreSQL transaction advisory lock: pg_advisory_xact_lock(tenantId, poNumber)
   └── Evaluates: EXACT_DUPLICATE (hash/amount match) vs. REVISED_PO (different amount) vs. NONE
           │
           ▼
[HospitalMasterService] ── Resolves canonical hospital & tenant-scoped aliases
           │
           ▼
[PoPersistenceHelper] ──── Persists ExtractedPurchaseOrder, line items, patient/billing/shipping details
           │
           ▼
[BodyAttachmentComparator] ── Cross-references body vs attachment; flags conflicts
           │
           ▼ (Job transitions to READY_FOR_REVIEW or NEEDS_REVIEW)
[PoWorkflowService]
   ├── Audited field update -> invalidates verification
   ├── Mathematical validation (PoValidationService) -> line item sum & balance equation checks
   ├── Verification check -> gates approval
   └── Approval -> transactionally supersedes previous approved revisions (is_superseded = true)
```

---

## 3. Database Schema Isolation

Two strictly isolated database schemas exist within the PostgreSQL instance:

### A. Schema `public` (UMIS Core)
* `t101_tenants`: Tenant isolation boundaries.
* `t201_mailboxes`: Mailbox connections, sync states, encrypted OAuth credentials.
* `t301_emails`: Normalized emails, MIME headers, processing status.
* `t302_attachments`: Attachment references, storage locations, MIME types, SHA-256 hashes.
* `t401_event_outbox`: Transactional outbox guaranteeing at-least-once message delivery.

### B. Schema `po_processor` (PO Intelligence)
* `processing_jobs`: Durable job tracking, distributed worker leases, extraction summaries, JPA `@Version`.
* `processing_job_attachments`: Associated attachment metadata and SHA-256 fingerprints.
* `hospitals`: Canonical hospital master entities.
* `hospital_aliases`: Raw hospital text string mappings to canonical hospital records.
* `extracted_purchase_orders`: Authoritative PO headers, currency, totals, revision tracking, supersession flags.
* `extracted_po_line_items`: Individual product line items, quantities, unit prices, line totals.
* `po_patient_details`: Sensitive patient references isolated at rest.
* `po_billing_details`: Invoicing party name, tax identifiers, billing addresses.
* `po_shipping_details`: Destination facility, delivery address, carrier, tracking codes.
* `po_document_analyses`: Per-attachment extraction classifications and processing diagnostics.
* `po_extraction_runs`: Telemetry logs (model name, prompt version, input/output tokens, duration, status).
* `po_field_evidence`: Attribution pointers linking extracted fields to document text and page numbers.
* `po_body_attachment_comparisons`: Side-by-side discrepancy reports between body and attachments.
* `po_validation_results`: Mathematical equation rules and business validation findings.
* `po_duplicate_detections`: Exact duplicate and revision lineage records.
* `po_approval_history`: Immutable audit trail for verify, approve, reject, and supersede events.
* `po_field_edit_history`: Audit trail for human reviewer field corrections.

---

## 4. Security & Compliance Architecture

1. **Authentication:** Stateless HMAC-SHA256 JWT tokens aligned with UMIS signing secret.
2. **Authorization (RBAC):** Role hierarchy enforced via `@PreAuthorize`:
   * `PO_VIEWER`: Dashboard read-only.
   * `PO_REVIEWER`: Authorized to edit extracted fields.
   * `PO_VERIFIER`: Authorized to verify mathematical consistency.
   * `PO_APPROVER`: Authorized to approve purchase orders.
   * `PO_ADMIN`: Administrative privileges (re-extraction, global settings).
3. **Tenant Isolation:** Client identity headers (`X-User-Id`, `X-Tenant-Id`) are distrusted. All database lookups are strictly bound to `principal.getTenantId()`. Cross-tenant queries return HTTP 404.
4. **Data Privacy:** Sensitive patient information (`po_patient_details`) is isolated at rest and omitted from list queries and standard logging.
5. **AI Safety:** All document content is wrapped inside `<untrusted_document_content>` tags with explicit prompt-injection defenses.
