# Processing Progress and Review Lock Architecture Design

**Document Reference:** `DESIGN-PROGRESS-LOCK-2026`  
**System Component:** `PO-Processor` / `frontend`  
**Date:** Monday, November 9, 2026  
**Architect:** Senior Java Spring Boot, PostgreSQL, RabbitMQ & React Architect  

---

## 1. Architectural Overview

To ensure that purchase order extraction is transparent to users and protected against race conditions and premature approvals, the PO Processor incorporates:
1. **Persistent Weighted Progress Tracking:** Real execution stage percentages stored in PostgreSQL and updated at discrete processing boundaries.
2. **Review Lock Security Enforcement:** Strict backend rejection (`HTTP 409 Conflict`) for any review, verification, or approval attempts while processing is active.
3. **Frontend Adaptive Polling:** Dynamic UI progress updates with automated transition to the review workspace once processing completes.

---

## 2. Progress Stage & Weight Model

Progress weights reflect real computational effort and resource latency:

| Stage Name | Weight | Cumulative | Stage Description |
| :--- | :---: | :---: | :--- |
| `EMAIL_RECEIVED` | 0% | 0% | RabbitMQ event consumed and job record persisted |
| `RETRIEVING_EMAIL_CONTENT` | 5% | 5% | Reading email body from storage abstraction |
| `RETRIEVING_ATTACHMENTS` | 10% | 15% | Bounded streaming of attachments (or skipped if 0 atts) |
| `EXTRACTING_DOCUMENT_TEXT` | 15% | 30% | PDFBox text extraction and OCR preflight |
| `AI_EXTRACTION` | 35% | 65% | Gemini 3.8 Flash document understanding |
| `EXTRACTING_LINE_ITEMS` | 15% | 80% | Parsing line-item tables, pricing, and hospital resolution |
| `VALIDATING_PO` | 10% | 90% | Balance equations, tax checks, side-by-side reconciliation |
| `PERSISTING_RESULTS` | 5% | 95% | Idempotent upsert in PostgreSQL schema |
| `COMPLETED` | 5% | 100% | Final state reached; ready for human review |

---

## 3. Database Schema (Flyway V7)

```sql
ALTER TABLE po_processor.processing_jobs
    ADD COLUMN IF NOT EXISTS progress_percentage INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_processing_stage VARCHAR(50) NOT NULL DEFAULT 'EMAIL_RECEIVED',
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS processing_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS processing_completed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS progress_message VARCHAR(255) DEFAULT 'Email received';
```

Mapped via `@Embeddable JobProcessingProgress` in `ProcessingJob.java`.

---

## 4. API Endpoints

### `GET /api/v1/processing-jobs/{id}/progress`
* **RBAC:** `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN`
* **Response:**
  ```json
  {
    "jobId": "40376b56-8196-4961-a329-dce08e58424e",
    "status": "PROCESSING",
    "stage": "AI_EXTRACTION",
    "progressPercentage": 65,
    "message": "Extracting purchase order details with Gemini AI",
    "attempt": 1,
    "startedAt": "2026-11-09T01:21:52Z",
    "updatedAt": "2026-11-09T01:21:54Z",
    "completedAt": null,
    "lastError": null
  }
  ```

### Review Lock on `GET /api/v1/processing-jobs/{id}/details`
* If job status is `PROCESSING`, `QUEUED`, or `PENDING`:
* Returns **HTTP 409 Conflict**:
  ```json
  {
    "status": 409,
    "error": "PROCESSING_IN_PROGRESS",
    "message": "Purchase order processing is currently underway (65%). Review will unlock upon completion.",
    "progressPercentage": 65,
    "stage": "AI_EXTRACTION"
  }
  ```

---

## 5. React Frontend Integration

1. **`PoJobsTable.tsx`:** Displays a mini progress bar and percentage badge for active jobs. Replaces the "Review" action with a spinning processing pill. Clicking is disabled while active.
2. **`PoJobDetailsPage.tsx`:** When accessing a processing job directly, renders a dedicated "PO Review Locked" card with stage description, gradient progress bar, and automated 1.5s polling. Transitions to full review tabs automatically when progress reaches 100%.
