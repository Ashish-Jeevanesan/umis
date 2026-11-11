# PROJECT: PO PROCESSOR — AI-POWERED PURCHASE ORDER INTELLIGENCE ENGINE

## 1. Objective

Extend the existing PO Processor Spring Boot application into an AI-powered Purchase Order Intelligence system.

The system must receive email events from UMIS, retrieve the complete email body and all attachments, understand the content using Google's Gemini API, extract structured purchase-order information, compare email and attachment information, detect duplicate purchase orders, maintain hospital purchasing history, and provide a human verification and approval workflow.

The system must support different purchase-order formats and layouts without depending on a fixed PDF template.

The existing UMIS application must remain independent and unchanged.

All PO-specific processing, extracted information, historical records, validation, editing, verification and approval must be owned by PO Processor.

## 2. Existing architecture — preserve it

### UMIS
- Java 21
- Spring Boot 3.3.4
- React + TypeScript + Vite frontend
- Supabase PostgreSQL
- Gmail / Microsoft Graph / IMAP integration
- RabbitMQ event publishing
- Local filesystem / S3-compatible email storage

### PO Processor
- Java 21
- Spring Boot 3.3.4
- Supabase PostgreSQL
- Dedicated database schema: `po_processor`
- RabbitMQ consumer
- Existing local filesystem storage reader
- Existing REST APIs
- Integrated React frontend

### Existing frontend routes

Keep the single existing React application:

- `/umis` — UMIS dashboard
- `/po` — PO Processor dashboard
- `/po/jobs/:id` — Individual PO processing job

Do not create another React application.

### Existing event flow

Gmail → UMIS → RabbitMQ → PO Processor → AI Processing → Supabase → Human Review → Approval

Do not modify the UMIS event contract or UMIS business logic.

---

# 3. AI processing architecture

Use Google Gemini API for document understanding and structured extraction.

The Gemini API key will be provided separately.

### Configuration

Store the API key only in the PO Processor backend environment.

Example:

```properties
GEMINI_API_KEY=<provided-locally>
GEMINI_MODEL=<configurable-supported-model>
GEMINI_ENABLED=true
```

Requirements:

- Never expose the API key to React.
- Never commit the API key.
- Never log the API key.
- Add placeholders to `.env.example`.
- Support configurable model selection.
- Use the official supported Google Gemini API/SDK.
- Implement API timeout handling, retries, rate-limit handling and error reporting.
- Avoid sending the same document repeatedly when a successful extraction result already exists.
- Record the model and prompt version used for every extraction.

Use an abstraction such as:

`DocumentIntelligenceProvider`

The first implementation should use:

`GeminiDocumentIntelligenceProvider`

This should allow another AI provider to be introduced later without rewriting the PO processing workflow.

Do not put all AI processing inside the RabbitMQ consumer.

The consumer should initialize the processing job and delegate document processing to an independent asynchronous processing service.

---

# 4. Complete email and attachment understanding

This is one of the most important requirements.

The system must read and understand BOTH:

1. The complete email body.
2. Every email attachment.

Do not depend only on the email subject or snippet.

### Email body processing

Retrieve the complete stored email body.

Support:
- Plain text.
- HTML.
- HTML-to-text conversion.
- Email signatures.
- Forwarded messages.
- Quoted email threads.
- Multiple sections and tables.
- Purchase-order details included directly in the email body.

Preserve the original body as an immutable source.

The AI must identify:
- Whether the email body contains actual PO information.
- Whether the body is merely a short introductory message.
- Whether the body contains instructions that conflict with the attachment.
- Whether the body contains patient or hospital information.
- Whether the email references a PO number or amount.
- Whether the body is unrelated to the purchase order.

### Attachment processing

Inspect every attachment.

Initially support:
- PDF.
- JPEG.
- JPG.
- PNG.
- TIFF.
- DOCX, where feasible.

Do not assume the first attachment is the purchase order.

Classify every attachment as:
- PURCHASE_ORDER
- INVOICE
- QUOTATION
- DELIVERY_NOTE
- SUPPORTING_DOCUMENT
- UNRELATED
- UNKNOWN

Multiple attachments may belong to the same purchase order.

Identify which attachments are relevant to the PO and whether multiple documents contain conflicting information.

Do not silently discard attachments.

Maintain attachment-level classification and processing status.

---

# 5. Digital PDF reading and OCR fallback

The extraction method must be explicitly recorded for every document.

Implement this processing sequence:

### Stage A: Digital PDF extraction

Use Apache PDFBox.

1. Read the PDF.
2. Attempt text extraction.
3. Evaluate whether the extracted text is usable.
4. Preserve extracted text and page information.
5. If usable, pass the extracted content to Gemini for semantic interpretation.

### Stage B: OCR fallback

If the PDF is scanned, image-based or contains insufficient extractable text:

1. Detect the need for OCR.
2. Process the document using an OCR engine.
3. Support multipage documents.
4. Preserve page-level extracted text.
5. Pass OCR output to Gemini.

Evaluate Tesseract or another appropriate OCR implementation.

The OCR provider must be replaceable.

### Stage C: Image-based documents

Support JPG, JPEG, PNG and TIFF using OCR or Gemini's supported document/image input capabilities.

### Required metadata

For every processed attachment, record:

- Original filename.
- MIME type.
- File size.
- SHA-256 hash.
- Page count, where applicable.
- Extraction method.
- Extraction engine.
- OCR usage.
- Extraction timestamp.
- Processing result.
- Error details.

The extraction method must distinguish:

- DIGITAL_TEXT_EXTRACTION
- OCR_FALLBACK
- IMAGE_VISION_EXTRACTION
- HYBRID_EXTRACTION

The dashboard must display the actual method used.

Never claim that a document was digitally extracted if OCR was used.

---

# 6. Email body versus attachment comparison

This is a mandatory feature.

The AI must compare the complete email body with the extracted purchase-order document.

The system must determine whether:

1. Email body and attachment contain matching PO information.
2. Email body contains a short introductory message and the attachment contains the actual PO.
3. Email body contains PO details but there is no PO attachment.
4. Email body is irrelevant, while the attachment contains the actual PO.
5. Email body and attachment contain conflicting PO numbers.
6. Email body and attachment contain different amounts.
7. Email body and attachment contain different delivery dates.
8. Multiple attachments contain conflicting purchase-order information.
9. No valid purchase order can be identified.

Store a structured comparison result.

Suggested classifications:

- BODY_AND_ATTACHMENT_MATCH
- ATTACHMENT_ONLY_RELEVANT
- BODY_ONLY_RELEVANT
- BODY_IRRELEVANT_ATTACHMENT_RELEVANT
- BODY_ATTACHMENT_MISMATCH
- MULTIPLE_DOCUMENT_CONFLICT
- NO_PURCHASE_ORDER_FOUND
- NEEDS_MANUAL_REVIEW

The system must compare at least:

- PO number.
- Hospital/customer.
- Vendor.
- PO date.
- Delivery date.
- Currency.
- Total amount.
- Tax.
- Line items.
- Payment terms.
- Billing address.
- Shipping address.

Create a comparison report showing:

| Field | Email Body | Attachment | Result |
|---|---|---|---|
| PO Number | PO-1001 | PO-1001 | MATCH |
| Currency | EUR | EUR | MATCH |
| Total | 2,500 | 2,500 | MATCH |
| Delivery Date | 10 Nov | 12 Nov | MISMATCH |

Preserve the original values.

Never automatically overwrite one source with another.

Conflicts must be highlighted for human review.

---

# 7. Purchase-order information to extract

Create a structured extraction model.

## A. Purchase-order header

Extract:

- PO number.
- PO title or description.
- PO date.
- Requested delivery date.
- Expected delivery date.
- Currency.
- Subtotal.
- Discount.
- Shipping/freight charges.
- Tax amount.
- Tax percentage.
- Grand total.
- Payment terms.
- Payment method, if available.
- Delivery instructions.
- Incoterms, if available.
- Contract/reference number.
- Quotation reference.
- Purchase-order status, if specified.

## B. Hospital / customer information

Extract:

- Hospital name.
- Legal entity name.
- Hospital code, if available.
- Department.
- Contact person.
- Contact email.
- Contact phone.
- Billing entity.
- Shipping entity.

## C. Vendor information

Extract:

- Vendor/company name.
- Vendor address.
- Vendor contact.
- Vendor email.
- Vendor phone.
- Vendor tax registration number.
- Vendor account/reference number.

## D. Patient information

Where provided, extract:

- Patient name.
- Patient ID / MRN.
- Patient reference number.
- Date of birth, only when necessary and explicitly present.
- Relevant procedure or case reference.
- Surgeon/doctor name, if present.
- Department or specialty.

Patient details are sensitive.

Requirements:
- Store only information necessary for the PO workflow.
- Do not infer missing patient information.
- Do not invent patient identities.
- Apply strict access control.
- Protect sensitive fields at rest.
- Do not send unnecessary patient details to Gemini.
- Do not display patient information in dashboard summaries or list views.
- Ensure logs never contain sensitive patient details.

## E. Billing information

Extract:

- Billing company/entity.
- Billing contact.
- Billing address.
- City.
- State/region.
- Postal code.
- Country.
- Tax identification number, if provided.

## F. Shipping information

Extract:

- Ship-to entity.
- Hospital/location name.
- Shipping contact.
- Shipping address.
- City.
- State/region.
- Postal code.
- Country.
- Delivery instructions.
- Requested delivery date.

## G. Line items

Extract every line item.

Fields:

- Line number.
- Product code / SKU.
- Manufacturer part number.
- Product name.
- Description.
- Category.
- Quantity.
- Unit.
- Unit price.
- Discount.
- Tax percentage.
- Tax amount.
- Line subtotal.
- Line total.
- Currency.
- Batch/lot reference, if available.
- Serial number, if available.

Do not truncate long line-item tables.

Support documents containing multiple pages and tables.

Do not assume every PO has the same columns.

---

# 8. Structured AI extraction and evidence

Require Gemini to return structured JSON matching a defined schema.

Validate all model responses against the application's DTO/schema.

Do not directly persist unvalidated AI-generated JSON.

For every important extracted field, store:

- Extracted value.
- Normalized value.
- Confidence score.
- Source type.
- Source attachment ID or email body reference.
- Source page number, if applicable.
- Supporting text snippet.
- Whether the value was inferred or explicitly present.

Missing values must be null or explicitly marked as not provided.

Never invent missing amounts, dates, vendors or patient details.

Confidence scores must not be treated as proof of correctness.

Use evidence-based extraction.

For example:

```json
{
  "poNumber": {
    "value": "PO-SQ23234",
    "confidence": 0.99,
    "sourceType": "ATTACHMENT",
    "sourceFile": "PO-SQ23234.pdf",
    "page": 1,
    "evidence": "Purchase Order No: PO-SQ23234"
  }
}
```

The exact JSON schema should be designed and validated by the backend implementation.

---

# 9. Historical hospital purchase-order tracking

The system must maintain a purchasing history for each hospital/customer.

Create a hospital/customer master record.

### Hospital matching

Normalize hospital names and maintain aliases.

For example:

- Drogresias Hospital
- Drogresias General Hospital
- Drogresias Hosp.

These may refer to the same hospital, but do not automatically merge records solely because names look similar.

Use supporting identifiers where available:
- Hospital code.
- Legal entity.
- Tax ID.
- Address.
- Country.
- Previously approved purchase orders.

Use AI to suggest possible matches, but require review when identity is uncertain.

### Historical information

For each hospital, display:

- Hospital name.
- Total number of approved POs.
- Total approved PO value by currency.
- First PO received.
- Most recent PO received.
- Total number of duplicate submissions.
- Historical PO list.
- PO numbers.
- PO dates.
- Individual PO amounts.
- Vendors involved.

### Historical total calculation

Only include approved, non-duplicate purchase orders.

Maintain separate totals for each currency.

Example:

| Currency | Historical approved PO total |
|---|---:|
| EUR | €125,000 |
| USD | $42,000 |
| INR | ₹850,000 |

Do not combine currencies without an explicit, documented exchange-rate conversion process.

Provide a hospital history section in the PO detail screen.

---

# 10. Duplicate PO detection

Duplicate detection is mandatory.

A hospital may send the same PO multiple times or resend a corrected document.

Implement duplicate detection using:

1. Hospital/customer identity.
2. Normalized PO number.
3. Vendor identity, where relevant.
4. Document SHA-256 hash.
5. Other supporting PO attributes.

### Duplicate handling

When an incoming document matches an existing approved or previously registered PO:

- Mark the incoming processing job as DUPLICATE.
- Store the duplicate detection result.
- Link it to the original PO record.
- Display the original PO details.
- Show the duplicate reason.
- Record the incoming email and attachment references.
- Do not create a second purchase-order header or duplicate line items.
- Do not add its amount to historical hospital totals.

Keep the duplicate email/job record for audit purposes.

### Important: corrected purchase orders

Do not automatically classify every document with an existing PO number as a duplicate.

A document may be:
- Exact duplicate.
- Revised purchase order.
- Amended purchase order.
- Cancelled/reissued purchase order.

Compare document hashes, dates, totals, revision indicators and line items.

Flag uncertain cases as NEEDS_REVIEW.

Allow an authorized user to link a revision to the original PO rather than creating an unrelated duplicate.

Implement database-level uniqueness constraints where appropriate, accounting for tenant and hospital identity.

Ensure concurrent duplicate processing cannot create duplicate PO records.

---

# 11. Verification, editing and approval workflow

This is a critical business requirement.

The system must provide a human review process.

### Proposed lifecycle

- RECEIVED
- PROCESSING
- READY_FOR_REVIEW
- NEEDS_REVIEW
- VERIFIED
- APPROVED
- DUPLICATE
- FAILED

Use additional statuses if required, but document their meaning and allowed transitions.

### PO details screen

Display extracted data in editable sections:

1. PO header.
2. Hospital/customer.
3. Vendor.
4. Patient details.
5. Billing information.
6. Shipping information.
7. Line items.
8. Taxes and totals.
9. Payment terms.
10. Email versus attachment comparison.
11. Historical hospital information.
12. Duplicate detection results.
13. Extraction evidence and confidence.
14. Processing method and document details.

### Edit functionality

Users must be able to edit extracted fields.

When any extracted field is changed:

- Mark the PO as EDITED.
- Record the original value.
- Record the new value.
- Record who changed it.
- Record the timestamp.
- Record the reason if provided.
- Invalidate previous verification.
- Require verification again.

Do not silently overwrite AI-extracted values.

Maintain an audit trail.

### Verify button

Provide a dedicated Verify button.

When clicked:

- Validate all mandatory fields.
- Validate financial calculations.
- Validate email/body/attachment conflicts.
- Validate duplicate status.
- Validate required hospital/vendor information.
- Check unresolved extraction warnings.
- Confirm that edits have been reviewed.
- Record the verification result, user and timestamp.

If validation fails, show exactly what must be corrected.

Verification must not automatically approve the PO.

### Approve button

Provide a separate Approve button.

Approval is the final human action.

Only an authorized user may approve.

A PO must not become APPROVED merely because:
- Gemini extracted it.
- The document passed AI confidence thresholds.
- The totals match.
- The user clicked Verify.

Approval requires successful verification.

On approval:

- Record the approving user.
- Record approval timestamp.
- Store the final approved data.
- Update the job to APPROVED or the agreed final processing status.
- Include the PO in historical hospital totals.
- Make the PO read-only by default.

Any subsequent change must use an explicit amendment/reopen workflow and must be audited.

### Important distinction

`VERIFIED` means the data has been checked.

`APPROVED` means an authorized person has finalized the PO.

`DUPLICATE` means the incoming submission has been identified as an existing PO and has not created another PO record.

---

# 12. Database design

Use the existing Supabase PostgreSQL database and `po_processor` schema.

The extracted information must be persisted in the database.

Do not store the only copy of extracted PO data in application memory or JSON files.

Use normalized relational tables for major business entities.

Suggested tables:

### Existing tables to preserve

- processing_jobs
- processing_job_attachments
- processed_events

Inspect existing schema before modifying anything.

### New tables

1. `hospitals`
   - Hospital master records.
   - Tenant association.
   - Normalized name.
   - Legal name.
   - Identifiers.
   - Address.
   - Aliases or matching references.

2. `hospital_aliases`
   - Alternative hospital names.
   - Mapping to canonical hospital.

3. `extracted_purchase_orders`
   - PO header.
   - Hospital relationship.
   - Vendor details.
   - PO number.
   - Dates.
   - Currency.
   - Subtotal.
   - Discount.
   - Freight.
   - Tax.
   - Grand total.
   - Payment terms.
   - Lifecycle status.
   - Approval information.

4. `extracted_po_line_items`
   - Product code.
   - Description.
   - Quantity.
   - Unit price.
   - Tax.
   - Discount.
   - Line total.
   - PO relationship.

5. `po_patient_details`
   - Patient-related information.
   - Restricted access and appropriate protection.

6. `po_billing_details`
   - Billing entity and address.

7. `po_shipping_details`
   - Shipping entity and address.

8. `po_document_analyses`
   - Document classification.
   - Extraction method.
   - OCR information.
   - Model information.
   - Processing result.

9. `po_extraction_runs`
   - Extraction attempts.
   - Model name.
   - Prompt version.
   - Input references.
   - Output status.
   - Errors.
   - Timestamps.

10. `po_field_evidence`
    - Field-level extracted values.
    - Confidence.
    - Source.
    - Page.
    - Supporting text.

11. `po_body_attachment_comparisons`
    - Comparison classification.
    - Field-level differences.
    - Conflicts.
    - Review status.

12. `po_validation_results`
    - Validation rule.
    - Result.
    - Severity.
    - Expected value.
    - Actual value.
    - Explanation.

13. `po_duplicate_detections`
    - Incoming job.
    - Original PO.
    - Duplicate type.
    - Detection reason.
    - Confidence.
    - Review status.

14. `po_approval_history`
    - Verification.
    - Approval.
    - Rejection.
    - Reopening.
    - User.
    - Timestamp.
    - Comments.

15. `po_field_edit_history`
    - Original value.
    - New value.
    - Field name.
    - Editor.
    - Timestamp.
    - Reason.

Use UUID primary keys, appropriate foreign keys, indexes, tenant isolation and audit fields.

Use precise PostgreSQL numeric types for financial values. Never use floating-point types for currency.

Use JSONB only where it is appropriate for flexible metadata, evidence, raw model output or document-specific information. Do not replace all relational structures with one giant JSONB column.

Design the schema carefully to avoid storing the same hospital or PO information redundantly.

---

# 13. Mandatory schema.sql requirement

This is a strict project requirement.

Maintain a canonical SQL file:

`PO-Processor/src/main/resources/schema.sql`

It must contain the complete database schema required by PO Processor.

Include:
- Schema creation.
- All tables.
- All columns.
- Data types.
- Primary keys.
- Foreign keys.
- Unique constraints.
- Indexes.
- Appropriate check constraints.
- Required defaults.

Whenever a table or database structure changes, update `schema.sql` in the same implementation task.

Spring Boot and Flyway may still manage migrations, but `schema.sql` must remain a complete and current representation of the database schema.

Do not use `schema.sql` as an uncontrolled startup script that drops or recreates production tables.

Use Flyway migrations for incremental database changes.

Set Hibernate schema generation to `validate` or an appropriate non-destructive setting.

Add a verification test that checks whether `schema.sql` is consistent with the intended Flyway-managed schema.

Never remove existing UMIS tables or modify the `public` schema.

---

# 14. PO Processor dashboard

Extend the existing dashboard under `/po`.

Do not create another React project.

### Dashboard summary cards

Display:

- Total emails received.
- Purchase orders identified.
- Ready for review.
- Needs manual review.
- Verified.
- Approved.
- Duplicates detected.
- Failed processing.

### Processing jobs table

Display:

- Email subject.
- Hospital.
- PO number, if extracted.
- Vendor.
- Total amount.
- Currency.
- Extraction method.
- Processing status.
- Received date.
- Last updated.

Support:
- Search.
- Hospital filtering.
- Status filtering.
- Date filtering.
- Currency filtering.
- Duplicate-only filtering.
- Pagination.
- Manual refresh.
- Automatic refresh.

### PO details page

Route:

`/po/jobs/:id`

Build a clear review interface.

Use tabs or structured sections:

- Overview.
- Extracted PO.
- Line items.
- Email and attachment comparison.
- Hospital history.
- Documents.
- Validation.
- Audit history.

Show the original email and document alongside extracted information.

Provide:
- Edit controls.
- Verify button.
- Approve button.
- Duplicate warning.
- Extraction method.
- Confidence and evidence.
- Processing history.

Do not expose filesystem paths or API keys.

### Hospital history

Provide a hospital history view accessible from a PO detail page.

Show:
- Historical approved POs.
- Total PO count.
- Historical totals grouped by currency.
- Previous PO numbers.
- Previous vendors.
- Previous dates.
- Current PO's contribution, only after approval.

---

# 15. Testing project — different PO layouts

I specifically need a test project for validating the AI extraction engine against different PO formats.

Do not test only one fixed PDF layout.

Create a dedicated testing area, for example:

`PO-Processor/test-data/purchase-orders/`

Organize the test data into categories.

### Required test document categories

1. Standard table-based purchase order.
2. Modern corporate branded PO.
3. Hospital-specific PO.
4. Multi-page purchase order.
5. Purchase order with multiple tax rates.
6. Purchase order with discounts.
7. Purchase order with shipping charges.
8. Purchase order with EUR currency.
9. Purchase order with USD currency.
10. Purchase order with INR currency.
11. Purchase order with unusual line-item columns.
12. PO with long product descriptions.
13. PO with multiple pages of line items.
14. Scanned PDF.
15. Low-quality scanned PDF.
16. Rotated scanned document.
17. Image-based purchase order.
18. Purchase order with handwritten annotations.
19. PO with missing fields.
20. PO with inconsistent totals.
21. PO with conflicting dates.
22. PO with conflicting email and attachment details.
23. Email containing all PO details but no attachment.
24. Email with an irrelevant body and valid PO attachment.
25. Email body and attachment containing matching information.
26. Email body and attachment containing mismatched amounts.
27. Multiple attachments containing different document types.
28. Duplicate PO document.
29. Revised/amended purchase order.
30. Document that is not a purchase order.

Use legally usable synthetic test documents or properly licensed fixtures.

Do not use actual patient information or real customer confidential documents in test fixtures.

### Ground-truth data

For every test document, provide an expected structured JSON file.

The test runner must compare extracted values against ground truth.

Measure:
- PO number accuracy.
- Vendor extraction accuracy.
- Hospital identification accuracy.
- Date accuracy.
- Currency accuracy.
- Amount accuracy.
- Tax accuracy.
- Line-item extraction accuracy.
- Email/attachment comparison accuracy.
- Duplicate detection accuracy.
- Document classification accuracy.

Report field-level precision/recall or appropriate exact-match accuracy.

Do not rely on a simple HTTP 200 response as proof of extraction accuracy.

### Repeatability

The test project must support:
- Individual test document execution.
- Batch execution.
- Re-running failed cases.
- Comparing different Gemini model configurations.
- Saving extraction results.
- Producing a test summary report.

Mock Gemini responses for deterministic unit tests.

Use actual Gemini calls only in separately configured integration tests.

Do not expose the API key in test reports.

---

# 16. Processing reliability and error handling

Implement reliable processing.

Requirements:

- Idempotent extraction.
- Retry handling.
- Gemini rate-limit handling.
- API timeout handling.
- Document corruption handling.
- OCR failure handling.
- Unsupported attachment handling.
- Partial extraction handling.
- Duplicate detection before PO persistence.
- Transactional persistence where appropriate.
- Audit trail for failed and repeated processing attempts.

A failed extraction must not cause loss of the original email or attachments.

A PO awaiting human review must not be repeatedly extracted on every refresh or service restart.

Do not acknowledge the RabbitMQ message as successfully processed before the durable processing job has been safely initialized.

Long-running AI processing should be independent of the RabbitMQ consumer thread.

Use explicit states for asynchronous processing.

---

# 17. Security and privacy

Purchase orders may contain patient and other confidential information.

Implement:

- Tenant isolation.
- Role-based authorization.
- Backend-only Gemini credentials.
- Restricted access to patient data.
- Secure storage of sensitive fields.
- No sensitive data in application logs.
- No API keys in browser bundles.
- Safe attachment access.
- Audit logging.
- Configurable retention policies.
- Appropriate protection of Gemini request/response data.

Before sending documents to Gemini, review what information is necessary and ensure the selected Google service and account configuration are appropriate for the organization's privacy and contractual requirements.

Do not assume that using an API key automatically makes processing compliant with healthcare privacy obligations.

---

# 18. API requirements

Create appropriate APIs for:

- Processing dashboard summary.
- PO extraction details.
- Extracted line items.
- Email/attachment comparison.
- Hospital history.
- Duplicate detection.
- Validation results.
- Field edits.
- Verification.
- Approval.
- Audit history.
- Re-extraction, when authorized.
- Test document execution and test reports.

Use DTOs and proper validation.

Never expose JPA entities directly.

Ensure tenant ownership is checked on every read and write.

---

# 19. Implementation phases

Do not attempt an uncontrolled all-at-once implementation.

Work in the following phases.

### Phase 1 — Architecture and database

- Inspect existing PO Processor.
- Design the extraction workflow.
- Design the normalized database schema.
- Create Flyway migrations.
- Create the complete `schema.sql`.
- Add entity and repository layers.
- Preserve existing processing functionality.

### Phase 2 — Document intelligence

- Implement complete email body retrieval.
- Implement attachment classification.
- Implement PDFBox extraction.
- Implement OCR fallback.
- Implement Gemini integration.
- Implement structured extraction.
- Implement field evidence and confidence.

### Phase 3 — Comparison and business intelligence

- Compare email body and attachments.
- Identify mismatches.
- Implement hospital identity matching.
- Implement historical PO totals.
- Implement duplicate and revision detection.

### Phase 4 — Validation and approval

- Implement financial validations.
- Implement editable extracted data.
- Implement field edit history.
- Implement Verify.
- Implement Approve.
- Implement approval audit history.

### Phase 5 — Frontend

- Extend the existing `/po` dashboard.
- Build extracted PO detail sections.
- Build hospital history.
- Build document comparison view.
- Build editing, verification and approval controls.
- Build duplicate warnings.

### Phase 6 — Testing

- Create diverse synthetic PO fixtures.
- Create expected extraction JSON files.
- Implement deterministic tests.
- Implement Gemini integration tests.
- Implement layout variation testing.
- Generate extraction accuracy reports.

### Phase 7 — Live verification

Use the existing test PO email workflow.

Test:
- Matching email and attachment.
- Irrelevant body with valid PO attachment.
- PO details in email body only.
- Mismatched amounts.
- Multiple attachments.
- Duplicate PO.
- Revised PO.
- Scanned PDF.
- Different currencies.
- Different hospital names and aliases.

Verify database persistence and dashboard results.

---

# 20. Final acceptance criteria

The implementation is not complete until:

1. Complete email body is read.
2. Every attachment is inspected and classified.
3. Digital PDF extraction is attempted first.
4. OCR is used when necessary.
5. Extraction method is recorded.
6. Gemini extracts the required PO information.
7. Email body and attachment information are compared.
8. Conflicts are highlighted.
9. Patient, vendor, billing, shipping and line-item information are persisted.
10. Historical hospital purchasing information is available.
11. Historical totals are grouped by currency.
12. Duplicate POs are detected and do not create another PO record.
13. Revised POs are distinguished from exact duplicates.
14. All extracted fields can be reviewed and edited.
15. Edits are audited and invalidate prior verification.
16. Verify and Approve are separate actions.
17. Approval is restricted to authorized users.
18. The existing `/po` dashboard exposes the complete workflow.
19. Diverse PO layouts are covered by the testing project.
20. `schema.sql` always reflects the complete current database schema.
21. Existing UMIS functionality and RabbitMQ ingestion continue working.
22. Automated tests pass.
23. Live Gemini integration tests demonstrate actual extraction quality.

---

# 21. Final implementation report

Provide:

- Architecture and processing workflow.
- Database ER diagram or schema description.
- Complete `schema.sql`.
- Flyway migration list.
- New backend modules and services.
- Gemini configuration instructions.
- OCR installation/configuration instructions.
- API documentation.
- Frontend screenshots or route descriptions.
- Test fixture inventory.
- Extraction accuracy report.
- Duplicate detection test results.
- Security and privacy considerations.
- Known limitations.
- Exact instructions to run the complete system locally.

Do not report a feature as completed merely because its code compiles.

Distinguish:
- Implemented.
- Unit tested.
- Integration tested.
- Live Gemini tested.
- Verified with real documents.

Start by inspecting the existing repository and producing a detailed implementation plan. Preserve the working UMIS integration and do not modify UMIS unless absolutely necessary.