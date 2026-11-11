# UMIS Mailbox Connectors Attachment Ingestion Audit

**Document Reference:** `AUDIT-ATTACHMENT-INGESTION-2026`  
**Execution Date:** Monday, November 9, 2026  
**Auditor:** Senior Java Spring Boot, PostgreSQL, RabbitMQ & React Architect  

---

## 1. Executive Summary

An audit of the UMIS email connectors (Gmail, Microsoft Graph, and Generic IMAP) was conducted to determine why recent purchase orders arrived in PO Processor with zero attachments and zero line items.

### Root Cause Identified: Gmail Attachment Sub-Resource Omission
* **Connector Affected:** `GmailConnector.java` and `GmailPayloadParser.java`.
* **Mechanism of Failure:** When a message with an attached file (e.g. PDF) is retrieved from the Gmail REST API via `/messages/{id}?format=full`, Gmail **does not return the attachment bytes inline** inside `part.body.data`. Instead, Gmail sets `part.body.data` to an empty string (`""`) and provides an `attachmentId` pointing to the external sub-resource `/messages/{messageId}/attachments/{attachmentId}`.
* **The Code Defect:** In `GmailPayloadParser.java` (lines 73–85):
  ```java
  String data = partNode.path("body").path("data").asText("");
  if (!filename.isBlank() && !data.isBlank()) { // <--- Evaluates to FALSE!
      // Decoded bytes added to attachments
  }
  ```
  Because `data.isBlank()` was true, `GmailPayloadParser` **silently discarded every non-inline attachment**.
* **Impact Downstream:**
  1. `GmailConnector` returned `attachments = []`.
  2. `EmailIngestionService` recorded `attachmentCount = 0` and stored zero files in `attachments/`.
  3. `EMAIL_INGESTED` was published with an empty `attachments` array.
  4. PO Processor was forced into body-only extraction, missing all line items contained in the attached PDF.

---

## 2. Connector Audit Comparison Matrix

| Connector | Metadata Retrieval | Byte Download Mechanism | Status Before Fix | Status After Fix |
| :--- | :--- | :--- | :--- | :--- |
| **Gmail** | `part.filename`, `mimeType`, `body.attachmentId` | Requires secondary GET to `/attachments/{attachmentId}` | **FAILED** (Silently dropped) | **VERIFIED** (Resolved via secondary call) |
| **Microsoft Graph** | `hasAttachments` flag, `attachments` expansion | `/me/messages/{id}/attachments` (`contentBytes`) | **PASSING** | **PASSING** |
| **Generic IMAP** | `Part.getDisposition()`, `getFileName()` | Direct `part.getInputStream()` stream transfer | **PASSING** | **PASSING** |

---

## 3. Implemented Structural Fix

### A. `GmailPayloadParser.java`
Introduced `extractAttachmentReferences` which captures `attachmentId` when inline data is empty:
```java
public record GmailAttachmentRef(String filename, String mimeType, long size, String attachmentId, byte[] inlineData) {}
```

### B. `GmailConnector.java`
Added automated resolution of external attachment references:
```java
private List<NormalizedAttachment> resolveGmailAttachments(
        HttpHeaders headers, String providerMessageId, List<GmailPayloadParser.GmailAttachmentRef> rawRefs) {
    List<NormalizedAttachment> attachments = new ArrayList<>();
    for (GmailPayloadParser.GmailAttachmentRef ref : rawRefs) {
        byte[] bytes = ref.inlineData();
        if ((bytes == null || bytes.length == 0) && ref.attachmentId() != null) {
            bytes = downloadGmailAttachment(headers, providerMessageId, ref.attachmentId());
        }
        if (bytes != null && bytes.length > 0) {
            attachments.add(NormalizedAttachment.of(ref.filename(), ref.mimeType(), bytes.length, HashUtils.sha256Hex(bytes), bytes));
        }
    }
    return attachments;
}
```

---

## 4. Verification & Testing

* **`GmailConnectorTest.java`:** Added `testFetchMessageDownloadsExternalAttachments` verifying mocked REST calls to `/attachments/{id}`. (8/8 tests passed).
* **Full UMIS Suite:** All 53 backend tests passing with zero failures (`mvn test`).
* **Line-Item Extraction Regression:** Verified end-to-end against corrected `PO-2026-MULTI-5` synthetic PDF fixture (Subtotal $14,550.00, Grand Total $14,897.00). See [`docs/LINE-ITEM-EXTRACTION-REGRESSION.md`](docs/LINE-ITEM-EXTRACTION-REGRESSION.md).
