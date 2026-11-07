# UMIS Object Storage Abstraction

## 1. Overview

Email bodies (plain text and HTML) and attachment binaries are never stored in PostgreSQL -
only structured metadata and a **relative storage key** live in the database
(`email_messages.body_text_storage_ref`, `body_html_storage_ref`,
`email_attachments.storage_reference`). The actual bytes live behind a small
backend-agnostic interface, `com.umis.email.service.ObjectStorageClient`, with two
implementations selected by `umis.storage.type` (env var `UMIS_STORAGE_TYPE`):

| Type (`UMIS_STORAGE_TYPE`) | Implementation | Bean | Backing |
|---|---|---|---|
| `LOCAL` (default) | `LocalFilesystemStorageClient` | `localFilesystemStorageClient` | Plain files under `umis.storage.local-base-path` |
| `S3` | `S3ObjectStorageClient` | `s3ObjectStorageClient` | MinIO or AWS S3 (`umis.storage.endpoint`/`bucket-name`/`access-key`/`secret-key`) |

Both beans are mutually exclusive via `@ConditionalOnProperty` on `S3StorageConfig.s3Client()`,
`S3ObjectStorageClient`, and `LocalFilesystemStorageClient` - exactly one is ever constructed,
and in `LOCAL` mode the AWS S3 SDK client is never instantiated at all (no network calls, no
AWS credentials needed). `EmailStorageService` is the only thing that talks to
`ObjectStorageClient` directly; every other layer (ingestion, REST API, RabbitMQ event
payloads) only ever sees the relative storage key string.

```java
public interface ObjectStorageClient {
    void putObject(String key, InputStream content, long contentLength, String contentType);
    default void putObject(String key, byte[] bytes, String contentType) { ... }
    DownloadedObject getObject(String key);
    record DownloadedObject(InputStream inputStream, String contentType, long contentLength) {}
}
```

## 2. Key layout (identical across both backends)

```
emails/<tenantId>/<mailboxId>/<emailId>/body.txt
emails/<tenantId>/<mailboxId>/<emailId>/body.html
attachments/<tenantId>/<emailId>/<attachmentId>/content
```

The attachment key is always the fixed segment `content`, never the original file name (even
sanitized) - the original name is preserved purely as metadata in
`email_attachments.file_name` and reattached only when serving a download's
`Content-Disposition` header. This keeps the storage key entirely free of
caller-influenced strings, which is also why it's safe to expose these keys in REST
responses and RabbitMQ event payloads: they're backend-agnostic relative paths built only
from our own UUIDs and tenant IDs, never absolute filesystem paths and never raw user input.

## 3. `LocalFilesystemStorageClient` guarantees

- **Atomic writes**: content is written to a temp file (`.upload-*.tmp`) in the *same*
  directory as the final target, then published via `Files.move(..., ATOMIC_MOVE,
  REPLACE_EXISTING)`. A crash or I/O failure mid-write can never leave a partially-written
  file visible at the real key - the move either fully succeeds or the temp file is cleaned
  up and the target is untouched.
- **Streaming reads**: `getObject()` returns `Files.newInputStream(path)` directly, not a
  buffered `byte[]` - serving a large attachment download doesn't require it to fit in the
  JVM heap.
- **Path traversal protection**: every key is resolved via `basePath.resolve(key).normalize()`
  and rejected with `IllegalArgumentException` unless the result still starts with
  `basePath`. Since keys are always built internally (never taken verbatim from a request),
  this is defense-in-depth, not the primary safeguard - but it's the only thing standing
  between a crafted key and the host filesystem if that assumption is ever violated.
- Non-fatal on failure: like the S3 client, a failed write is logged and does not abort
  email ingestion (matches the existing "don't lose the message metadata because storage
  had a bad day" behavior).

## 4. Configuration

```bash
UMIS_STORAGE_TYPE=LOCAL                          # or S3
UMIS_STORAGE_LOCAL_BASE_PATH=D:/UMIS-Data/emails  # LOCAL only; defaults to ./storage if unset

# S3 only:
UMIS_STORAGE_ENDPOINT=http://localhost:9000
UMIS_STORAGE_BUCKET=umis-emails
UMIS_STORAGE_ACCESS_KEY=minioadmin
UMIS_STORAGE_SECRET_KEY=minioadmin
```

No storage location or credential is hardcoded anywhere in source - everything above has an
env var, and `UMIS_STORAGE_LOCAL_BASE_PATH` falls back to the portable relative default
`./storage` if you don't set it, never to a hardcoded absolute path.

For local (non-Docker) `start-services.ps1` runs, set these in the root `.env` file - the
launcher forwards exactly these two storage keys (see the comments in `start-services.ps1`
next to `$storageEnvKeys`). For Docker Compose, `UMIS_STORAGE_TYPE` defaults to `S3` (the
stack provisions a real MinIO container) and `UMIS_STORAGE_LOCAL_BASE_PATH` is pre-set to
`/app/storage`, backed by the `umis_local_storage` named volume - switch
`UMIS_STORAGE_TYPE=LOCAL` in `.env` to use it instead.

## 5. Migrating objects between S3 and LOCAL

Switching `UMIS_STORAGE_TYPE` **never** moves, copies, or reinterprets existing objects -
it only changes which backend new reads/writes go through. A `storage_reference` written
under one backend is a relative key, not a backend-specific URL, so moving the underlying
bytes to the same relative layout under the other backend is all that's required - no
database changes.

**S3/MinIO -> LOCAL**: for each object key in the bucket, download it to
`<UMIS_STORAGE_LOCAL_BASE_PATH>/<same key>`. Using the MinIO client (`mc`) against the
default Docker Compose MinIO:

```powershell
mc alias set umis-minio http://localhost:9000 minioadmin minioadmin
mc mirror umis-minio/umis-emails "D:\UMIS-Data\emails"
```

**LOCAL -> S3/MinIO**: mirror the other direction, preserving the same relative paths:

```powershell
mc alias set umis-minio http://localhost:9000 minioadmin minioadmin
mc mirror "D:\UMIS-Data\emails" umis-minio/umis-emails
```

Either way, run the mirror while the backend is stopped (or at least not actively writing)
to avoid copying a file mid-write, then flip `UMIS_STORAGE_TYPE` and restart. Existing rows
in `email_messages`/`email_attachments` need no changes - their `storage_reference` values
already match the key layout on both sides.

## 6. Testing

`backend/src/test/java/com/umis/email/service/`:

- `LocalFilesystemStorageClientTest` - put/get round-trip with a multi-megabyte payload
  (exercises the streaming path, not just a trivial write), tenant directory isolation,
  path traversal rejection (both `/`- and `\`-style), an interrupted write leaving no
  partial file or stray temp file, and a clear error on a missing key.
- `StorageProviderSelectionTest` - proves `UMIS_STORAGE_TYPE=LOCAL`/`S3`/unset each activate
  exactly one `ObjectStorageClient` bean and never construct the other (including never
  constructing the AWS `S3Client` at all in `LOCAL` mode).

Pre-existing coverage this abstraction relies on and doesn't duplicate:
`EmailIngestionServiceTest#testDuplicateEmailDeduplication` (duplicate ingestion by
`providerMessageId`), `MessagingAndOutboxTest` (RabbitMQ publishing and the transactional
outbox).
