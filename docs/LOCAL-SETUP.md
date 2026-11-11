# UMIS + PO Processor — Windows Local Setup & Developer Guide

## 1. System Requirements & Prerequisites

* **Operating System:** Windows 10 / 11 / Server 2022 (x64)
* **Java Development Kit:** OpenJDK 21 (JDK 21 is mandatory; JDK 17 is not supported)
* **Node.js:** v20.x or v22.x LTS with npm 10+
* **PostgreSQL:** Version 16.x running on port `5432`
* **RabbitMQ:** Version 3.13.x with Management Plugin enabled on port `5672` (Web UI on `15672`)
* **Build Tools:** Apache Maven 3.9+

---

## 2. Infrastructure Port Allocation

| Service | Protocol / Port | Purpose | Health Endpoint |
| :--- | :---: | :--- | :--- |
| **UMIS Backend** | HTTP / `8080` | Mailbox ingestion, OAuth, outbox publisher | `http://localhost:8080/actuator/health` |
| **PO Processor** | HTTP / `8085` | AI extraction, reconciliation, review workflow | `http://localhost:8085/actuator/health` |
| **Unified Frontend**| HTTP / `3000` | Single-page React 18 + Vite dashboard | `http://localhost:3000/po` |
| **PostgreSQL** | TCP / `5432` | Relational database (`public` & `po_processor`) | `pg_isready -p 5432` |
| **RabbitMQ** | AMQP / `5672` | Message broker exchange (`umis.events`) | `amqp://localhost:5672` |
| **RabbitMQ UI** | HTTP / `15672` | Broker management web console | `http://localhost:15672` |

---

## 3. Automated Windows Startup

The repository includes PowerShell scripts to orchestrate dependencies and microservices:

```powershell
# 1. Start core infrastructure (PostgreSQL, RabbitMQ, directories)
.\start-services.ps1

# 2. In a second PowerShell terminal, start UMIS Backend:
cd backend
.\run-backend.ps1

# 3. In a third PowerShell terminal, start PO Processor:
cd PO-Processor
.\run.ps1

# 4. In a fourth PowerShell terminal, start React Frontend:
cd frontend
npm install
npm run dev
```

---

## 4. Manual Configuration & Environment Setup

### A. Database Initialization
Ensure PostgreSQL is active and create the database if not present:
```sql
CREATE DATABASE umis_db;
```
Flyway automatically creates and migrates schemas `public` (UMIS) and `po_processor` (PO Processor) on startup.

### B. PO Processor Configuration (`PO-Processor/.env`)
Copy `.env.example` to `.env`:
```powershell
cd PO-Processor
Copy-Item .env.example .env
```
Populate `.env` with your environment values:
```bash
# Server Port
SERVER_PORT=8085

# PostgreSQL Connection
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/umis_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_postgres_password

# RabbitMQ
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest

# JWT Authentication (UMIS Shared HMAC-SHA256 Secret)
PO_JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# Google Gemini AI Integration
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-3.8-flash
GEMINI_ENABLED=true
GEMINI_TIMEOUT_SECONDS=60
GEMINI_MAX_OUTPUT_TOKENS=8192

# Memory & Attachment Safety Limits
PO_MAX_ATTACHMENT_SIZE_BYTES=26214400      # 25 MB
PO_MAX_TOTAL_ATTACHMENT_BYTES=104857600    # 100 MB
PO_MAX_PDF_PAGES=50
PO_MAX_DOCX_CHARS=500000
PO_MAX_IMAGE_DIMENSION=4096

# Worker Leasing & Retry
PO_WORKER_POLL_INTERVAL_MS=3000
PO_WORKER_MAX_RETRIES=3
PO_WORKFLOW_ALLOW_SELF_APPROVAL=false
```

---

## 5. Running Automated Tests

To execute the complete Maven test suite (67 unit, integration, and security tests):

```powershell
cd PO-Processor

# Configure JDK 21 environment
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

# Clear external datasource URL so in-memory test H2 database is used
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue

# Execute test suite
mvn test
```

### Running the Live Gemini 3.8 Flash Benchmark
```powershell
$env:GEMINI_API_KEY = "your_actual_gemini_api_key"
mvn test -Dtest=LivePoGeminiExtractionBenchmarkRunner
```

### Verifying Frontend Production Build
```powershell
cd frontend
npm run build
```

---

## 6. Troubleshooting Common Issues

1. **`java.lang.UnsupportedClassVersionError: class file version 65.0`:**
   * Cause: System is running on JDK 17 instead of JDK 21.
   * Fix: Explicitly point `$env:JAVA_HOME` to JDK 21 before executing Maven commands.
2. **`H2 Syntax Error: Table "extracted_purchase_orders" not found` during tests:**
   * Cause: The external environment variable `SPRING_DATASOURCE_URL` is pointing to an external PostgreSQL instance rather than the in-memory test database.
   * Fix: Run `Remove-Item Env:SPRING_DATASOURCE_URL` in your PowerShell session.
3. **`Gemini API HTTP 404 Not Found`:**
   * Cause: The legacy model `gemini-1.5-flash` was deprecated on Google's endpoint.
   * Fix: Ensure `GEMINI_MODEL=gemini-3.8-flash` is set in your environment.
4. **Port 8085 / 8080 Already in Use:**
   * Fix: Find and terminate the blocking process:
     ```powershell
     Get-Process -Id (Get-NetTCPConnection -LocalPort 8085).OwningProcess | Stop-Process -Force
     ```
