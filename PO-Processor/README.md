# PO Processor — Independent UMIS Downstream Consumer

The **PO Processor** is an independently deployable Spring Boot microservice designed to consume email-ingestion events from the **Universal Mailbox Integration Service (UMIS)** over RabbitMQ.

It retrieves raw email content and attachments from UMIS storage (read-only), manages its own Purchase Order processing records in a dedicated database schema, enforces idempotency, and reports processing outcomes back to UMIS via its acknowledgement API.

---

## 1. Architectural Boundaries & Responsibilities

| Responsibility | UMIS Microservice | PO Processor Microservice |
| :--- | :--- | :--- |
| **Mailbox Integrations** | Owns Gmail, M365, IMAP connections & sync | None (completely decoupled from mailbox protocols) |
| **Email Storage** | Owns raw email bodies & attachment storage | Read-only streaming access to email bodies/attachments |
| **Database Ownership** | Owns `public` schema (17 tables) | Owns `po_processor` schema (3 tables) |
| **Event Publication** | Publishes `EMAIL_INGESTED` to RabbitMQ | Consumes `EMAIL_INGESTED`, ACKs upon local commit |
| **Downstream Outcomes** | Exposes `/api/v1/emails/{id}/acknowledgements` | Reports status (`PROCESSING`, `SUCCESS`, `FAILED`) |
| **Future Extraction** | Strictly out of scope | Document parsing, LLM/AI extraction, review UI |

---

## 2. RabbitMQ Topology & Event Contract

- **Exchange**: `umis.events` (`TopicExchange`, Durable)
- **Routing Key**: `umis.events.email.ingested`
- **Consumer Queue**: `po-processor.queue.email-ingested` (Durable)
- **Dead-Letter Exchange (DLX)**: `umis.dlx` (`DirectExchange`, Durable)
- **Dead-Letter Routing Key**: `po-processor.dlq`
- **Dead-Letter Queue (DLQ)**: `po-processor.queue.dlq` (Durable)
- **Acknowledgement Mode**: `AcknowledgeMode.MANUAL`
- **Delivery Guarantees**:
  - Message ACK (`channel.basicAck`) is executed **only after** the local database transaction commits.
  - If processing fails, the message is requeued up to `po.rabbitmq.max-retries` (default: 3).
  - Poison or malformed messages are routed directly to the DLQ (`channel.basicNack(..., false, false)`).

### Consumed Canonical Event Contract (`EMAIL_INGESTED` v1.0)
```json
{
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "eventType": "EMAIL_INGESTED",
  "eventVersion": "1.0",
  "occurredAt": "2026-11-08T03:30:00Z",
  "tenantId": "080390dc-5284-4ead-8ea7-b061ea6bdf81",
  "mailboxId": "f0e15baf-04d5-4e1d-8925-ffbd6355f813",
  "payload": {
    "emailId": "d3b07384-d113-408d-8fb0-18e00185989e",
    "mailboxId": "f0e15baf-04d5-4e1d-8925-ffbd6355f813",
    "tenantId": "080390dc-5284-4ead-8ea7-b061ea6bdf81",
    "subject": "Purchase Order PO-1001 – ABC Medical",
    "sender": "purchasing@abcmedical.com",
    "senderName": "ABC Medical Hospital",
    "recipients": ["ashishjwork09@gmail.com"],
    "snippet": "Please find attached our purchase order PO-1001...",
    "bodyTextStorageRef": "emails/080390dc.../f0e15baf.../d3b07384.../body.txt",
    "bodyHtmlStorageRef": "emails/080390dc.../f0e15baf.../d3b07384.../body.html",
    "hasAttachments": true,
    "attachments": [
      {
        "attachmentId": "c4791a82-2022-4458-963d-0b73167b5e40",
        "fileName": "PO_PO-1001.pdf",
        "contentType": "application/pdf",
        "size": 2517,
        "storageReference": "attachments/080390dc.../d3b07384.../c4791a82.../content",
        "fileHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      }
    ]
  }
}
```

---

## 3. Database Schema Ownership (`po_processor`)

PO Processor connects directly to PostgreSQL (Supabase) via JDBC and operates in its own isolated schema:

```text
po_processor
├── processing_jobs              # Purchase order processing lifecycles
├── processing_job_attachments   # Associated email attachments (status & storage refs)
└── processed_events             # Event idempotency registry (event_id, consumer_name)
```

- **Migrations**: Managed by Flyway (`V1__init_po_processor_schema.sql`).
- **Idempotency**: Redelivered messages matching an existing `event_id` and consumer code are detected instantly and acknowledged without creating duplicate records.
- **Isolation**: Zero modifications, tables, or constraints are added to the UMIS `public` schema.

---

## 4. Storage Access (Read-Only)

PO Processor implements a secure `StorageReader` with path traversal guards:

- **Property**: `PO_STORAGE_LOCAL_BASE_PATH` (defaults to `../backend/storage`).
- **Path Traversal Protection**: Rejects storage keys containing `..` or escaping the storage root.
- **Streaming**: Large attachments are streamed via `InputStream` rather than buffered in the heap.
- **Read-Only**: No delete or write operations exist in `StorageReader`.

---

## 5. UMIS Acknowledgement Client

After persisting the local job record, PO Processor calls UMIS:

- **Endpoint**: `POST /api/v1/emails/{emailId}/acknowledgements`
- **Request Body**:
  ```json
  {
    "consumerModule": "PO_PROCESSING",
    "status": "PROCESSING",
    "processedAt": "2026-11-08T03:32:00Z",
    "referenceId": "JOB-UUID",
    "message": "PO processing job initialized and ready for extraction",
    "idempotencyKey": "event-uuid"
  }
  ```
- **Authentication**: If configured, `UmisClient` obtains and caches a JWT bearer token from `POST /api/v1/auth/login`, auto-refreshing on 401 responses.

---

## 6. Quick Start & Operational Instructions

### 1. Environment Configuration

Copy `.env.example` to `.env`:

```bash
cd PO-Processor
cp .env.example .env
```

Configure your datasource, RabbitMQ, and UMIS endpoints in `.env`.

### 2. Launch the Application

#### Option A: Windows 1-Click Launcher
Double-click `run.bat` (or execute `.\run.ps1` in PowerShell).

#### Option B: Maven Command
```bash
cd PO-Processor
mvn spring-boot:run
```

The service will start on port **8085** (configurable via `SERVER_PORT`).

---

## 7. Inspection REST Endpoints

Verify processing jobs and health status using the built-in REST API:

- **Health Check**: `GET http://localhost:8085/api/v1/health`
- **List All Jobs**: `GET http://localhost:8085/api/v1/processing-jobs`
- **Get Job by ID**: `GET http://localhost:8085/api/v1/processing-jobs/{id}`
- **Get Job by UMIS Email ID**: `GET http://localhost:8085/api/v1/processing-jobs/email/{emailId}`
- **Actuator Health**: `GET http://localhost:8085/actuator/health`

---

## 8. Automated Tests

Run the full verification test suite (19 tests covering validation, storage, path traversal, consumer ACKs/NACKs, dead-lettering, and database integration):

```bash
mvn test
```
