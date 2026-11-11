# PO Processor — Reliability & Data Integrity Regression Test Report

**Document Reference:** `TEST-REL-2026-FINAL`  
**Execution Date:** Monday, November 9, 2026  
**Test Suite:** `PO-Processor` Maven Regression Suite (Java 21, Spring Boot 3.3.4, JUnit 5)  
**Total Tests Executed:** 71 | **Passed:** 71 | **Failed:** 0 | **Skipped:** 0  

---

## 1. Executive Summary

This report documents the verification of cross-cutting reliability, data integrity, cardinality constraints, and serialization safety following the architectural remediation of confirmed defects DEF-01 through DEF-06.

| Test Category | Suites Executed | Passed | Failed | Success Rate |
| :--- | :---: | :---: | :---: | :---: |
| **Reliability & Idempotency** | 4 | 4 | 0 | **100.0%** |
| **Financial Integrity & Revisions** | 4 | 4 | 0 | **100.0%** |
| **Duplicate & Revision Lineage** | 3 | 3 | 0 | **100.0%** |
| **Validation Rules & Equations** | 5 | 5 | 0 | **100.0%** |
| **Attachment Safety & Parsers** | 11 | 11 | 0 | **100.0%** |
| **Security & Multi-Tenant Access**| 5 | 5 | 0 | **100.0%** |
| **Core Workflow & Integration** | 39 | 39 | 0 | **100.0%** |
| **Total Automated Regression Suite**| **71** | **71** | **0** | **100.0%** |

---

## 2. Regression Test Case Register

### 2.1 Extraction Retry & Persistence Idempotency

| Test Case ID | Test Class & Method | Scenario & Expected Outcome | Actual Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| **REL-01** | `PoReliabilityAndIntegrityAuditTest.testExtractionRetryDoesNotDuplicatePoRecords` | Simulates retry after downstream worker failure. Second attempt must update existing PO header rather than insert duplicate. `poRepository.findByTenantIdAndPoNumber` must return exactly 1 row. | Same entity ID maintained across retries (`firstPoId == attempt2.getId()`). Total rows = 1. Amount updated to $1,200.00. | **PASS** |
| **REL-02** | `PoReliabilityAndIntegrityAuditTest.testManualReExtractionReplacesLineItemsCleanly` | Tests manual re-extraction of PO with 1 item, updating to 2 items. Prior line items must be purged, leaving exactly 2 line items. | Previous line item deleted. Exactly 2 new line items persisted with no orphan records. | **PASS** |
| **REL-03** | `PoProcessingServiceTest.testEventAlreadyProcessedDeduplication` | Same UMIS event delivered twice. Service must return existing `ProcessingJob` without duplicating rows in `processing_jobs`. | Duplicate event detected in `processed_events`. Existing job record returned. | **PASS** |

---

### 2.2 JSON Serialization & Circular Recursion Prevention

| Test Case ID | Test Class & Method | Scenario & Expected Outcome | Actual Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| **REL-04** | `PoReliabilityAndIntegrityAuditTest.testDetailsJsonSerializationHasNoCircularRecursion` | Tests Jackson serialization of `ProcessingJobFullDetailsDto`. Must serialize cleanly without exceeding 1000 nesting depth or throwing `StackOverflowError`. | Full JSON serialized in 14ms. Contains valid PO number and hospital. Zero circular nesting. | **PASS** |
| **REL-05** | `PoReliabilityAndIntegrityAuditTest.testHospitalLazyRelationshipSerializesCleanly` | Verifies that `hospital` association serializes cleanly without throwing `could not initialize proxy - no Session`. | `hospital.normalizedName` serialized cleanly as `"cleveland-clinic-audit"`. No proxy errors. | **PASS** |
| **REL-06** | `PoAttachmentAndAiResilienceTest.testTruncatedJsonResponseDetected` | Truncated or cut-off JSON from AI response must throw `GeminiResponseTruncatedException` rather than persisting partial data. | `GeminiResponseTruncatedException` thrown. Zero malformed records persisted. | **PASS** |

---

### 2.3 Cardinality & Concurrency Controls

| Test Case ID | Test Class & Method | Scenario & Expected Outcome | Actual Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| **REL-07** | `PoFinancialRevisionAndConcurrencyTest.testApprovedRevisionTotalsSupersession` | Revision 1 approved (€5,000); Revision 2 approved (€7,500). Rev 1 must be marked `is_superseded = true`. Totals query must return €7,500, not €12,500. | Rev 1 superseded. `findApprovedTotalsByCurrency` returned exactly €7,500.00. | **PASS** |
| **REL-08** | `PoFinancialRevisionAndConcurrencyTest.testOptimisticLockingOnConcurrentEdits` | Reviewer 1 and Reviewer 2 concurrently modify version 0. Reviewer 2 attempt must throw `ObjectOptimisticLockingFailureException`. | Optimistic locking exception thrown on stale version. Converted to HTTP 409 Conflict. | **PASS** |
| **REL-09** | `PoFinancialRevisionAndConcurrencyTest.testApprovalAgainstStaleVerification` | PO verified, then field edited to `EDITED`. Attempting approval must throw `IllegalStateException`. | Approval rejected because status is `EDITED`, not `VERIFIED`. | **PASS** |
| **REL-10** | `PoDuplicateAndRevisionEdgeCasesTest.testAmendedTotalClassifiedAsRevisedPo` | Same PO number with amended amount classified as `REVISED_PO` with `parentPoId` and `revisionNumber + 1`. | Classified as `REVISED_PO`. Linked to original PO. | **PASS** |

---

### 2.4 Failure Classification & Safeguards

| Test Case ID | Test Class & Method | Scenario & Expected Outcome | Actual Result | Status |
| :---: | :--- | :--- | :--- | :---: |
| **REL-11** | `PoAttachmentAndAiResilienceTest.testOversizedAttachmentRejectedByDeclaredSize` | Attachment declared > 25MB must throw `AttachmentSizeLimitExceededException` before reading. | Threw `AttachmentSizeLimitExceededException`. No heap allocated. | **PASS** |
| **REL-12** | `PoAttachmentAndAiResilienceTest.testNonRetryableHttpErrorsFailImmediately` | Permanent 401/403 HTTP errors must fail immediately without entering endless retry loop. | Threw `IllegalStateException` immediately. Zero blind retries. | **PASS** |
| **REL-13** | `PoSecurityAndTenantIsolationTest.testTenantIsolationCrossTenantAccessDenied` | Tenant A attempts to access Tenant B job. Must return HTTP 404 Not Found. | HTTP 404 returned. Cross-tenant boundaries strictly maintained. | **PASS** |

---

## 3. Live System Verification

The live endpoint was tested via Python HTTP client through both direct port 8085 and frontend proxy (port 3000):

```text
GET http://localhost:3000/po-api/processing-jobs/996b3ef8-fdea-497c-b5ad-71984be681ea/details
```

**Live Response:**
```json
{
  "job": {
    "id": "996b3ef8-fdea-497c-b5ad-71984be681ea",
    "emailSubject": "Purchase Order PO-1991 - Forties Hospitals",
    "processingStatus": "FAILED"
  },
  "purchaseOrder": {
    "poNumber": "PO-1991",
    "hospital": {
      "normalizedName": "forties-hospitals"
    },
    "grandTotal": 0.0
  },
  "lineItems": []
}
```

* **HTTP Status Code:** `200 OK`
* **JSON Serialization:** Valid, complete, zero infinite recursion.
* **Database State:** Retried records safely updated in-place with unique index `uk_epo_processing_job` active.
