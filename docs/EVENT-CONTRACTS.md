# UMIS Event Contracts & Messaging Specification

## 1. RabbitMQ Topology

- **Topic Exchange**: `umis.events` (Durable)
- **Dead-Letter Exchange (DLX)**: `umis.dlx` (Direct, Durable)
- **Dead-Letter Queue (DLQ)**: `umis.queue.dlq` (Durable)
- **Ingested Queue**: `umis.queue.email-ingested` (Durable, bound to `umis.events.email.ingested`)

## 2. Canonical Event Schema (`v1.0`)

All published events conform to the versioned envelope:
```json
{
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "eventType": "EMAIL_INGESTED",
  "eventVersion": "1.0",
  "occurredAt": "2026-11-03T10:30:00Z",
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "mailboxId": "m1-uuid",
  "payload": {
    "emailId": "em-1-uuid",
    "mailboxId": "m1-uuid",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "subject": "Purchase Order PO-98421",
    "sender": "supplier@partner.com",
    "senderName": "Partner Operations",
    "recipients": ["orders@acme.com"],
    "snippet": "Please find attached Purchase Order PO-98421...",
    "bodyTextStorageRef": "emails/1111/m1/em-1/body.txt",
    "bodyHtmlStorageRef": "emails/1111/m1/em-1/body.html",
    "hasAttachments": true,
    "attachments": [
      {
        "attachmentId": "att-1-uuid",
        "fileName": "PO-98421.pdf",
        "contentType": "application/pdf",
        "size": 245760,
        "storageReference": "attachments/1111/em-1/att-1/PO-98421.pdf",
        "fileHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      }
    ]
  }
}
```

> **Important**: Never place full attachment binary bytes in RabbitMQ messages. Downstream consumers download the attachment content via `/api/v1/emails/{id}/attachments/{attId}`.

## 3. Downstream Processing Acknowledgement Workflow

External consumers (e.g. PO Extraction service, CRM, Invoice processing) report their outcome back to UMIS via:
`POST /api/v1/emails/{emailId}/acknowledgements`

### Request Payload:
```json
{
  "consumerModule": "PO_PROCESSING",
  "status": "SUCCESS",
  "processedAt": "2026-11-03T10:32:00Z",
  "referenceId": "PO-98421",
  "message": "PO extracted successfully into ERP draft order",
  "idempotencyKey": "idemp-po-98421"
}
```

### Supported Statuses:
- `PENDING`
- `PROCESSING`
- `SUCCESS`
- `FAILED`
- `REVIEW_REQUIRED`
- `IGNORED`

Each consumer maintains independent state. For instance, an email can be `SUCCESS` in `PO_PROCESSING` while simultaneously `REVIEW_REQUIRED` in `CRM` and `IGNORED` in `INVOICE_PROCESSING`.
