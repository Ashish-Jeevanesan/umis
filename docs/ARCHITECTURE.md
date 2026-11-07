# Universal Mailbox Integration Service (UMIS) Architecture

## 1. High-Level Architecture Overview

The Universal Mailbox Integration Service (UMIS) is a production-grade, multi-tenant microservice engineered to handle automated mailbox connectivity, email ingestion, metadata normalization, binary attachment offloading to object storage, and event-driven downstream distribution.

```
+-----------------------------------------------------------------------------------+
|                               UMIS Ecosystem                                      |
+-----------------------------------------------------------------------------------+
|  Providers: Microsoft Graph (M365) | Gmail API | Generic IMAP | Unified APIs       |
+------------------------------------------+----------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------+
|                       Provider-Agnostic Ingestion Engine                          |
|  - MailboxConnector Interface            - Content Deduplication (SHA-256)        |
|  - AES-256 Credential Encryption         - S3 Binary Storage (MinIO / AWS S3)     |
+------------------------------------------+----------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------+
|                       Transactional Outbox & RabbitMQ                             |
|  - EventOutbox Table                     - Topic Exchange: umis.events            |
|  - Guaranteed at-least-once delivery     - DLX & Dead-Letter Queue: umis.queue.dlq|
+------------------------------------------+----------------------------------------+
                                           |
                                           +-----------------------+
                                           |                       |
                                           v                       v
                              +-------------------------+ +-------------------------+
                              |   PO Processing Module  | |    CRM / Sales Module   |
                              | (Extracts PO data)      | | (Links customer leads)  |
                              +------------+------------+ +------------+------------+
                                           |                           |
                                           +------------+--------------+
                                                        |
                                                        v
                                        +-------------------------------+
                                        | POST /emails/{id}/acknowledgements
                                        | (Independent Processing Status)|
                                        +-------------------------------+
```

## 2. Core Architectural Principles

1. **Provider Independence**: Third-party protocols (M365 Graph, Gmail API, IMAP, Nylas/Unipile) are isolated behind the `MailboxConnector` interface. The ingestion pipeline only processes `NormalizedEmail` and `NormalizedAttachment`.
2. **Strict Multi-Tenant Isolation**: Tenant scoping is verified through cryptographically signed JWT tokens and explicit organizational membership. Client-supplied tenant IDs are never blindly trusted.
3. **Decoupled Business Processing**: PO extraction, invoice processing, and CRM classification remain strictly outside UMIS. UMIS emits `EMAIL_INGESTED` events and records downstream outcomes via independent acknowledgements.
4. **Storage Separation**: Large raw MIME bodies and binary attachments are offloaded to S3-compatible object storage. Relational databases only store structured metadata and storage URIs.
5. **Guaranteed Delivery via Transactional Outbox**: Email persistence and outbox event recording occur in the same database transaction, eliminating dual-write inconsistencies.
