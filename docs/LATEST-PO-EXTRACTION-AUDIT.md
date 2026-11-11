# Latest Purchase Order Extraction Audit & Line-Item Investigation

**Document Reference:** `AUDIT-PO-LATEST-2026`  
**Execution Date:** Monday, November 9, 2026  
**Auditor:** Senior Java Spring Boot, PostgreSQL, RabbitMQ & React Architect  

---

## 1. Latest Processed Jobs Inventory

An empirical query of the PostgreSQL database (`po_processor.processing_jobs` and `extracted_purchase_orders`) identified the 5 most recent processing jobs:

| Job ID | Received At | Subject | Sender | Status | PO Number | Customer / Hospital | Total | Lines | Atts |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: |
| `40376b56-8196-4961-a329-dce08e58424e` | 2026-11-09 00:58 | PO-1993 - Forties Hospitals | Globus Medical | `READY_FOR_REVIEW` | `PO-1993` | Forties Hospitals | $12,306.00 | **0** | **0** |
| `c6fce915-4bde-4b52-9c0c-13ce88d87f6f` | 2026-11-09 00:14 | PO-1992 - Forties Hospitals | Globus Medical | `NEEDS_REVIEW` | `PO-1992` | Forties Hospitals | $17,745.00 | **0** | **0** |
| `996b3ef8-fdea-497c-b5ad-71984be681ea` | 2026-11-08 23:44 | PO-1991 - Forties Hospitals | Globus Medical | `FAILED` | `PO-1991` | Forties Hospitals | $0.00 | **0** | **0** |
| `f23d557c-22a1-4405-9c7e-7274ef04ee8d` | 2026-11-08 22:53 | PO-1990 - Forties Hospitals | Globus Medical | `FAILED` | `PO-1990` | None | $0.00 | **0** | **0** |
| `2aa69fec-fa52-430e-8629-e3b769cdd714` | 2026-11-08 00:35 | PO-SQ23234 New Surgery PO | Globus Medical | `READY_FOR_EXTRACTION` | None | None | None | **0** | **0** |

---

## 2. Root Cause of Missing Line Items on Recent POs

### Investigation Findings
1. **Source Document Verification:**  
   Physical inspection of the stored email files on disk (`backend/storage/emails/.../391fdd42.../body.txt`) revealed that the test emails transmitted by the generator contain **only header metadata and no itemized table**:
   ```text
   Hello,
   Please find attached our purchase order PO-1993.
   Customer: Forties Hospitals
   PO Number: PO-1993
   PO Date: 2026-11-09
   Delivery Date: 2026-11-18
   Currency: USD
   Total Amount: USD 12,306.00
   Please review the attached purchase order document.
   Regards,
   Forties Hospitals
   ```
2. **Zero Attachments Ingested:**  
   Every recent email in the database had `attachmentCount = 0` (`attachments: []`). The PDF attachment was omitted or not transmitted during email dispatch.
3. **AI Prompt Instructions:**  
   In `GeminiPromptBuilder.java`, the security directive explicitly instructs:  
   `"- Do NOT hallucinate or invent data. If a field is not present, set it to null."`  
   Because the source document contained no line items, the AI engine truthfully returned `lineItems: []`.
4. **Architectural Confirmation:**  
   The AI engine correctly refused to fabricate line items out of thin air. When an email contains only a grand total and no line-item details, the grand total must be preserved while line items are explicitly identified as unavailable.
5. **Prompt Enhancement:**  
   To guarantee that attachments and emails that *do* contain tables and lists have all items captured without omission, explicit extraction instructions will be added to `GeminiPromptBuilder.java`.

---

## 3. End-to-End Extraction Pipeline Trace

```
[Inbound Email / RabbitMQ Event]
                │
                ▼
[PoEmailIngestedConsumer] ── Saves ProcessingJob (preserves bodyStorageRef)
                │
                ▼ (QUEUED)
[PoJobWorkerService] ────── Acquires lease; initiates Progress (0% -> 15%)
                │
                ▼ (30%)
[StorageReader & Extractors] Reads body.txt & attachments (PDF/DOCX/Image)
                │
                ▼ (65%)
[Gemini 3.8 Flash Engine] ─ Extracts PO model, dates, amounts, and itemized rows
                │
                ▼ (80%)
[Line-Item & Details Engine] Processes line items, patient, billing, shipping details
                │
                ▼ (90%)
[Validation & Reconciliation] Checks 0.02 balance tolerance; cross-references body vs PDF
                │
                ▼ (95%)
[PoPersistenceHelper] ───── Idempotent upsert in po_processor schema
                │
                ▼ (100% COMPLETED)
[Review Lock Release] ───── Unlocks "Open Review" action in React UI
```
