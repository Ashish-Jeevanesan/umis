# Purchase Order (PO) Email & PDF Generator

A lightweight, standalone testing utility for the **Universal Mailbox Integration Service (UMIS)**.

This utility allows you to enter sample Purchase Order data, dynamically generate a clean business PO PDF document, and dispatch an email with the PDF attached via SMTP directly to your mailbox connected to UMIS (such as Gmail).

UMIS can then discover and ingest the email and attachment using its standard synchronization pipeline.

---

## Architecture & Isolation

- **Standalone & Isolated**: Lives completely under `tools/po-email-generator/`. It does **not** touch or alter any UMIS core code, database schema, or Spring Boot dependencies.
- **No Direct UMIS API Calls**: Emails are sent through standard SMTP to your external mailbox so UMIS experiences a 100% authentic, real-world incoming email workflow.
- **Pure JavaScript PDF Generation**: Uses `pdfkit` to generate vector-rendered PDFs in-memory buffer without headless Chrome or heavy binaries.
- **Zero Credential Exposure**: SMTP credentials reside exclusively in the local `.env` file on the server. No credentials or passwords are ever exposed to client-side scripts.

---

## Prerequisites

- **Node.js**: v18+ (Node.js v22 is already present in this workspace)
- **SMTP Account**: Any SMTP provider (Gmail, Office 365, Mailgun, Amazon SES, or a local SMTP mock server like Mailpit).

---

## Quick Start

### 1. Configure SMTP Credentials

Copy `.env.example` to `.env`:

```bash
cd tools/po-email-generator
cp .env.example .env
```

Open `.env` in an editor and set your SMTP credentials:

```ini
PORT=3050

# Gmail SMTP Example
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-account@gmail.com
SMTP_PASSWORD=xxxx xxxx xxxx xxxx
SMTP_FROM_EMAIL=your-account@gmail.com
SMTP_FROM_NAME=ABC Medical Supplies
SMTP_SECURE=false
```

#### Setting up a Gmail App Password:
1. Go to your **Google Account** -> **Security**.
2. Under "How you sign in to Google", ensure **2-Step Verification** is turned ON.
3. Search for or navigate to **App passwords** (`https://myaccount.google.com/apppasswords`).
4. Enter an app name (e.g. `UMIS PO Generator`) and click **Create**.
5. Copy the 16-character generated password and paste it into `SMTP_PASSWORD` in `.env`.

---

### 2. Launch the Utility

#### Option A: Windows 1-Click Launcher
Double-click `run.bat` in `tools/po-email-generator/` (or run `.\run.ps1` in PowerShell).

#### Option B: Manual Command
```bash
cd tools/po-email-generator
npm install
npm start
```

The web interface will automatically open at:
**`http://localhost:3050`**

---

## Features & Usage

1. **Email Routing**:
   - **To Email Address**: Enter the email address of the mailbox monitored by your UMIS connection (e.g. your Gmail address).
   - **Email Subject**: Subject line (e.g., `Purchase Order PO-1001 – ABC Medical`).
   - **Sender / Customer Name**: Display name for the email.

2. **PO Header Details**:
   - PO Number, PO Date, Customer Name, Currency, Payment Terms, Delivery Date, Billing and Shipping Addresses.

3. **Line Items**:
   - Dynamically add or remove line items.
   - Automatically calculates line item totals, subtotal, tax amount, and grand total.

4. **1-Click Convenience**:
   - **Load Sample PO**: Pre-populates realistic orthopedic / medical device purchase order data.
   - **Preview PDF**: Generates the PDF and opens it in a new browser tab for immediate visual inspection without sending an email.
   - **Send Email with PO PDF**: Generates the PDF, builds the dynamic email body, attaches `PO_<PO_NUMBER>.pdf`, and dispatches it via SMTP.

---

## Verifying UMIS Ingestion

1. Once the email is sent, log into your target mailbox (e.g., Gmail) to verify delivery.
2. In UMIS:
   - If using the **Gmail Connector** with automatic watch or polling, UMIS will pick up the email during the next sync cycle.
   - Or trigger a manual sync in the UMIS UI (`/mailboxes` -> **Sync Now**).
3. View the ingested PO in the UMIS dashboard:
   - Check the **Emails** view to see the message, metadata, and body.
   - Check the **Attachments** drawer to verify `PO_<PO_NUMBER>.pdf` was offloaded and can be downloaded from object storage.
   - Downstream consumers (e.g. `PO_PROCESSING`) will receive the `EMAIL_INGESTED` event with attachment metadata.

---

## API Endpoints

- `POST /api/send-po-email`: Validates PO payload, creates PDF buffer, and dispatches email via SMTP.
- `POST /api/preview-pdf`: Validates PO payload and streams the generated PDF buffer directly to the browser.
- `GET /api/smtp-status`: Returns non-sensitive SMTP configuration readiness status.
