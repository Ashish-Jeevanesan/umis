# UMIS Local Development (Windows, no AWS S3/MinIO required)

This profile runs the full ingestion pipeline on Windows - PostgreSQL + RabbitMQ (via Docker)
and the Spring Boot backend + React frontend natively - while storing email bodies and
attachments as plain files on disk instead of requiring MinIO or AWS S3.

## 1. Prerequisites

- Java 21 LTS, Apache Maven 3.8+, Node.js 20+/npm 10+ (see root `README.md`)
- Docker Desktop (for PostgreSQL + RabbitMQ) - **not** required if you're pointing at an
  external Postgres instead (e.g. Supabase; see step 3b)

## 2. Start infrastructure (PostgreSQL + RabbitMQ)

### Option A - Docker Compose (recommended for a self-contained local stack)

```powershell
cd "D:\Ashish_P\Project X\UMIS"
docker compose -f docker\docker-compose.yml up -d postgres rabbitmq
```

This starts only `postgres` and `rabbitmq` (not `minio`, `backend`, or `frontend` - those are
for the full containerized deployment, not this native-backend local profile). RabbitMQ's
management dashboard is at <http://localhost:15672> (guest/guest by default).

### Option B - External PostgreSQL (e.g. Supabase)

Skip the Docker `postgres` container and instead set `SPRING_DATASOURCE_URL` /
`SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` in `.env` to an external
Postgres connection string (Supabase Dashboard -> Project Settings -> Database -> Connection
string, **Session pooler** - the Transaction pooler doesn't support the server-side prepared
statements Hibernate relies on). You still need RabbitMQ:

```powershell
docker compose -f docker\docker-compose.yml up -d rabbitmq
```

## 3. Configure `.env`

Copy `.env.example` to `.env` in the repo root if you haven't already, then set:

```bash
# Local filesystem storage - no MinIO/S3 needed
UMIS_STORAGE_TYPE=LOCAL
UMIS_STORAGE_LOCAL_BASE_PATH=D:/UMIS-Data/emails

# If using Docker's own Postgres (Option A above), leave SPRING_DATASOURCE_* unset -
# application-dev.yml already defaults to jdbc:postgresql://localhost:5432/umis_db.
#
# If using an external Postgres (Option B), set all three:
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/postgres
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

`UMIS_STORAGE_LOCAL_BASE_PATH` is optional - if you leave it unset, it defaults to `./storage`
(relative to wherever the backend process runs, i.e. `backend/storage/`). Set it to an
absolute path like `D:/UMIS-Data/emails` if you want email data kept on a specific drive
outside the repo.

`start-services.ps1` reads `.env` itself and forwards exactly these keys into the backend
process - a plain `mvn spring-boot:run` invoked directly (not through the launcher) will
**not** pick up `.env` automatically; export the variables in that PowerShell session first,
or use the launcher.

## 4. Start the backend and frontend

```powershell
cd "D:\Ashish_P\Project X\UMIS"
.\start-services.ps1
```

This will:
1. Load `.env` (OAuth credentials, datasource, and storage settings).
2. Pick free ports for backend/frontend, rewriting `frontend/vite.config.ts` and
   `frontend/.env` accordingly.
3. Select the `dev` Spring profile automatically if `SPRING_DATASOURCE_URL` is set in `.env`
   (external Postgres), otherwise detect a local Postgres on port 5432 (Docker Compose),
   falling back to the embedded H2 file database only if neither is available.
4. Launch the backend and frontend each in their own console window.

Watch the backend console for:
```
[OK] Loaded from .env: UMIS_STORAGE_TYPE, UMIS_STORAGE_LOCAL_BASE_PATH, ...
...
Local filesystem object storage ready at: D:\UMIS-Data\emails
```

### Manual alternative (no launcher)

```powershell
# Backend - set env vars in this session first (see step 3), then:
cd "D:\Ashish_P\Project X\UMIS\backend"
$env:UMIS_STORAGE_TYPE = "LOCAL"
$env:UMIS_STORAGE_LOCAL_BASE_PATH = "D:/UMIS-Data/emails"
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=dev"

# Frontend, in a separate PowerShell window:
cd "D:\Ashish_P\Project X\UMIS\frontend"
npm install
npm run dev
```

## 5. Verify

```powershell
# Backend health
curl http://localhost:8080/actuator/health

# Confirm local storage is actually active (not S3) via the authenticated system-health endpoint
# (see docs/API.md for the auth flow) - componentDetails.storage should read
# "LOCAL_FILESYSTEM_READY", not "S3_MINIO_READY".
```

Connect a mailbox (the `MOCK` provider needs no external credentials) and trigger a sync from
the Mailboxes tab; confirm files appear under `UMIS_STORAGE_LOCAL_BASE_PATH`:

```powershell
Get-ChildItem -Recurse "D:\UMIS-Data\emails" | Select-Object -First 20
```

## 6. Switching back to S3/MinIO

Set `UMIS_STORAGE_TYPE=S3` in `.env` (and the `UMIS_STORAGE_ENDPOINT`/`BUCKET`/
`ACCESS_KEY`/`SECRET_KEY` values, or start the `minio` Docker Compose service for local
MinIO), then restart. Existing objects under either backend are untouched by switching -
see `docs/STORAGE-ABSTRACTION.md` section 5 if you want to migrate previously-stored
objects to the other backend.

## Related documentation

- `docs/STORAGE-ABSTRACTION.md` - the `ObjectStorageClient` interface, both
  implementations' guarantees, and the S3<->LOCAL migration procedure.
- `docs/ARCHITECTURE.md`, `docs/DATABASE.md`, `docs/MAILBOX-INTEGRATIONS.md` - broader
  system design, unaffected by this profile.
- Root `CLAUDE.md` - commands, module layout, and the `start-services.ps1` port-discovery
  and `.env`-forwarding behavior in full.
