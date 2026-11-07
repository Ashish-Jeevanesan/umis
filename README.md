# Universal Mailbox Integration Service (UMIS)

UMIS is a production-oriented, standalone, reusable, multi-tenant mailbox integration microservice. It connects customer mailboxes across multiple providers, normalizes incoming emails and attachments, maintains ingestion records, publishes domain events downstream via RabbitMQ, and provides tenant-level and global operational dashboards.

> **Scope Boundary**: UMIS is strictly a generic mailbox integration and ingestion service. It is **NOT** a PO or invoice extraction service. Business-specific document parsing, machine learning extraction, and classification remain outside UMIS in consumer modules.

---

## Key Features

- **Multi-Tenant by Design**: Strict cryptographic tenant isolation, role-based access (`GLOBAL_ADMIN`, `TENANT_ADMIN`, `TENANT_VIEWER`, `SERVICE_CLIENT`).
- **Provider Connectivity Framework**: Pluggable `MailboxConnector` interface supporting:
  - **Microsoft 365 / Outlook** via Microsoft Graph API (PKCE OAuth, Delta queries, Webhooks).
  - **Gmail / Google Workspace** via Gmail API (OAuth 2.0, History API sync, Pub/Sub watch).
  - **Generic IMAP** via Jakarta Mail (SSL/TLS, UID-based incremental sync).
  - **Unified Email API Adapter** (Nylas / Unipile integration interface).
  - **Simulated Mock Sandbox** for zero-external-dependency local testing and demonstration.
- **S3 Object Storage Offloading**: Raw HTML/Text bodies and binary attachments are saved directly to MinIO or AWS S3. Relational databases never store large binaries.
- **Transactional Outbox & RabbitMQ**: Guaranteed at-least-once downstream event delivery via `event_outbox` table, `umis.events` topic exchange, dead-letter exchanges (DLX), and dead-letter queues (DLQ).
- **Independent Downstream Consumer Acknowledgements**: Independent tracking of consumer module processing outcomes (`PO_PROCESSING`, `CRM`, `INVOICE_PROCESSING`) with idempotency key support.
- **Dual Operational Dashboards**: Global Administrator view (cross-tenant KPIs, system health, trends) and Tenant Administrator view (organization KPIs, mailbox controls, email activity, attachment download).

---

## Technology Stack

- **Backend**: Java 21 LTS, Spring Boot 3.3.4, Spring Security (JWT), Spring Data JPA, Spring AMQP (RabbitMQ), Flyway, S3 SDK, Jakarta Mail, OpenAPI 3 / Swagger UI.
- **Database**: PostgreSQL 16 (H2 in compatibility mode for unit/integration tests).
- **Object Storage**: MinIO (local dev) / AWS S3 (production).
- **Messaging**: RabbitMQ 3.13 with Management Console and DLQ.
- **Frontend**: React 18, TypeScript, Vite, TanStack Query, Tailwind CSS, Recharts, Lucide React.
- **Infrastructure**: Docker Compose, multi-stage Alpine Dockerfiles.

---

## Quick Start (Local Development)

### 1. Prerequisites
- Java 21 LTS
- Apache Maven 3.8+
- Node.js 20+ / npm 10+
- Docker & Docker Compose (optional, for containerized stack)

### 2. Automated Dual-Console Launch (Recommended)
You can launch both the backend and frontend with a single script or double-click:
```powershell
.\start-services.ps1
```
Or double-click `start-services.bat` in Windows Explorer.

**What the launcher does automatically:**
1. Tests network ports starting from 8080 (backend) and 3000 (frontend) to find free ports.
2. Updates `frontend/vite.config.ts` and `frontend/.env` with the assigned open ports and backend proxy target.
3. Launches the Spring Boot backend in a dedicated console window streaming live logs.
4. Launches the React Vite frontend in a separate console window streaming live logs.

### 3. Manual Launch (Alternative)
- **Backend**:
  ```bash
  cd backend
  mvn clean spring-boot:run
  ```
- **Frontend**:
  ```bash
  cd frontend
  npm install
  npm run dev
  ```

- Frontend Dashboard: [http://localhost:3000](http://localhost:3000)
- Swagger UI / OpenAPI docs: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Actuator Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Automated Test Verification

To run the complete automated test suite (including tenant isolation tests, deduplication tests, and consumer acknowledgement tests):
```bash
cd backend
mvn clean test
```

To run the production frontend build:
```bash
cd frontend
npm run build
```

---

## Detailed Documentation Suite

- [Architecture Overview](file:///D:/Ashish_P/Project%20X/UMIS/docs/ARCHITECTURE.md)
- [Database Schema & Migrations](file:///D:/Ashish_P/Project%20X/UMIS/docs/DATABASE.md)
- [Mailbox Integrations & Providers](file:///D:/Ashish_P/Project%20X/UMIS/docs/MAILBOX-INTEGRATIONS.md)
- [Event Contracts & RabbitMQ](file:///D:/Ashish_P/Project%20X/UMIS/docs/EVENT-CONTRACTS.md)
- [REST API Specification](file:///D:/Ashish_P/Project%20X/UMIS/docs/API.md)
- [Deployment & Infrastructure](file:///D:/Ashish_P/Project%20X/UMIS/docs/DEPLOYMENT.md)
- [Security Model & Isolation](file:///D:/Ashish_P/Project%20X/UMIS/docs/SECURITY.md)
- [Implementation Status & Verification](file:///D:/Ashish_P/Project%20X/UMIS/docs/IMPLEMENTATION-STATUS.md)
