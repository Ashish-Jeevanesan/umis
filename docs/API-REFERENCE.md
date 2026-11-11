# PO Processor — REST API Specification & Contract Reference

**Base URL:** `http://localhost:8085/api/v1`  
**Protocol:** HTTP/1.1 over TLS / JSON  
**Authentication:** HTTP Authorization Header with Bearer JWT  

---

## 1. Authentication & Security Model

All API endpoints (except Spring Actuator health probes) require a stateless HMAC-SHA256 JWT signed with the shared secret.

### Request Header
```http
Authorization: Bearer <token>
```

### JWT Claims Payload
```json
{
  "sub": "user@hospital.org",
  "userId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "PO_APPROVER",
  "exp": 1794285600
}
```

### Role-Based Access Control (RBAC) Matrix

| Role | View Dashboard | Edit Fields | Verify PO | Approve PO | Reject PO | Admin Re-Extract |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `PO_VIEWER` | Yes | No | No | No | No | No |
| `PO_REVIEWER` | Yes | **Yes** | No | No | No | No |
| `PO_VERIFIER` | Yes | Yes | **Yes** | No | No | No |
| `PO_APPROVER` | Yes | Yes | Yes | **Yes** | **Yes** | No |
| `PO_ADMIN` | Yes | Yes | Yes | Yes | Yes | **Yes** |

---

## 2. Standard Error Response Schema

All errors return RFC 7807 compatible JSON payloads:

```json
{
  "status": 409,
  "error": "CONFLICT",
  "message": "The record was updated by another transaction. Please refresh to inspect the latest changes.",
  "timestamp": "2026-11-09T03:59:20.123Z"
}
```

| HTTP Status | Error Code | Description |
| :---: | :--- | :--- |
| `400` | `BAD_REQUEST` | Validation error, unparseable payload, or illegal status transition |
| `401` | `UNAUTHORIZED` | Missing, malformed, or expired JWT bearer token |
| `403` | `FORBIDDEN` | Authenticated principal lacks required RBAC role |
| `404` | `NOT_FOUND` | Resource not found, or belongs to another tenant (tenant isolation) |
| `409` | `CONFLICT` | Concurrent modification detected via JPA `@Version` optimistic locking |
| `500` | `INTERNAL_SERVER_ERROR`| Unhandled server exception |

---

## 3. Endpoints Directory

### 3.1 List Processing Jobs
* **Endpoint:** `GET /processing-jobs`
* **Required Role:** `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN`
* **Query Parameters:**
  * `page` (int, default: 0): Zero-based page index.
  * `size` (int, default: 15): Number of items per page.
  * `status` (string, optional): Filter by `QUEUED`, `PROCESSING`, `READY_FOR_REVIEW`, `NEEDS_REVIEW`, `DUPLICATE`, `COMPLETED`, `FAILED`.
  * `search` (string, optional): Case-insensitive match on PO number, hospital name, or email sender.
* **Success Response (200 OK):**
  ```json
  {
    "content": [
      {
        "id": "7dacc442-242c-4f4a-90bf-69970e0774ff",
        "tenantId": "550e8400-e29b-41d4-a716-446655440000",
        "processingStatus": "READY_FOR_REVIEW",
        "emailSubject": "Purchase Order PO-2026-9982",
        "emailSender": "orders@cleveland.org",
        "receivedAt": "2026-11-09T03:10:00Z",
        "extractedSummary": {
          "poNumber": "PO-2026-9982",
          "hospitalName": "Cleveland Clinic",
          "totalAmount": 7500.00,
          "currency": "EUR"
        }
      }
    ],
    "page": 0,
    "size": 15,
    "totalElements": 1,
    "totalPages": 1
  }
  ```

---

### 3.2 Get Full Job Review Details
* **Endpoint:** `GET /processing-jobs/{id}/full-details`
* **Required Role:** `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN`
* **Description:** Returns the full aggregate graph powering the 7 review tabs.
* **Success Response (200 OK):** Returns `ProcessingJobFullDetailsDto` including:
  * `job`: Header and execution timestamps.
  * `purchaseOrder`: Extracted PO header, currency, workflow status, revision lineage.
  * `lineItems`: List of product line items with calculated subtotals.
  * `comparison`: Side-by-side reconciliation report and conflict count.
  * `validation`: Mathematical balance results and rule findings.
  * `hospitalHistory`: Multi-currency lifetime totals for the hospital.
  * `documents`: Per-attachment classification and OCR diagnostics.
  * `approvalHistory`: Complete chronological audit trail.

---

### 3.3 Edit Extracted Field
* **Endpoint:** `POST /processing-jobs/{id}/fields`
* **Required Role:** `PO_REVIEWER`, `PO_ADMIN`
* **Request Body:**
  ```json
  {
    "fieldName": "grandTotal",
    "newValue": "7550.00",
    "reason": "Correcting tax inclusion based on page 2 footer"
  }
  ```
* **Side Effects:**
  * Updates field value on `ExtractedPurchaseOrder`.
  * Logs old and new values in `po_field_edit_history`.
  * **Invalidates verification:** Sets `workflowStatus` to `EDITED`, requiring re-verification.

---

### 3.4 Verify Purchase Order
* **Endpoint:** `POST /processing-jobs/{id}/verify`
* **Required Role:** `PO_VERIFIER`, `PO_ADMIN`
* **Request Body:**
  ```json
  {
    "comments": "Verified line items sum to subtotal and match invoice contract"
  }
  ```
* **Enforced Business Rule:** Runs `PoValidationService`. If error count $> 0$, verification fails with HTTP 400.
* **Side Effects:** Sets `workflowStatus` to `VERIFIED`, enabling the `Approve` button.

---

### 3.5 Approve Purchase Order
* **Endpoint:** `POST /processing-jobs/{id}/approve`
* **Required Role:** `PO_APPROVER`, `PO_ADMIN`
* **Request Body:**
  ```json
  {
    "comments": "Approved for ERP posting"
  }
  ```
* **Enforced Business Rules:**
  1. PO must be in `VERIFIED` status; unverified POs are rejected.
  2. Separation of duties: Self-approval is rejected unless `po.workflow.allow-self-approval=true`.
* **Side Effects (Transactional Revision Supersession):**
  * Marks approved PO: `is_approved = true`, `is_superseded = false`, `workflow_status = 'APPROVED'`.
  * Marks previous approved revisions: `is_superseded = true`, `workflow_status = 'SUPERSEDED'`.
  * Records `SUPERSEDED` audit event in `po_approval_history`.
  * Marks `processing_jobs` status as `COMPLETED`.

---

### 3.6 Reject Purchase Order
* **Endpoint:** `POST /processing-jobs/{id}/reject`
* **Required Role:** `PO_APPROVER`, `PO_ADMIN`
* **Request Body:**
  ```json
  {
    "comments": "Rejected: Pricing does not match contracted catalog"
  }
  ```
* **Side Effects:** Sets `workflowStatus` to `REJECTED`, sets `isApproved = false`. The previous approved revision remains active and effective.

---

### 3.7 Hospital Lifetime Purchasing History
* **Endpoint:** `GET /hospitals/{id}/history`
* **Required Role:** `PO_VIEWER`, `PO_REVIEWER`, `PO_VERIFIER`, `PO_APPROVER`, `PO_ADMIN`
* **Success Response (200 OK):**
  ```json
  {
    "hospitalId": "8fae0000-399c-4755-a044-00acb047dc75",
    "hospitalName": "Cleveland Clinic",
    "totalsByCurrency": [
      {
        "currency": "EUR",
        "approvedCount": 1,
        "totalAmount": 7500.00
      },
      {
        "currency": "USD",
        "approvedCount": 3,
        "totalAmount": 42000.00
      }
    ]
  }
  ```
* **Financial Guarantee:** Strictly excludes superseded revisions, unapproved POs, and duplicates. Currencies remain strictly segregated without arbitrary exchange-rate conversions.
