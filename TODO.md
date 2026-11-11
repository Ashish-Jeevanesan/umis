# TODO

## Gmail connector: history.list pagination not followed (potential missed-mail risk)

**File:** `backend/src/main/java/com/umis/mailbox/connector/impl/GmailConnector.java`, `fetchViaHistory()` (lines 248-268)

**Issue:** When `GET /history?startHistoryId=X&maxResults=50` returns more records than fit on
one page, Gmail includes a `nextPageToken`. The current code never checks for or follows it —
it processes only the first page's `messagesAdded`, then unconditionally reads the response's
top-level `historyId` field and stores that as the new sync cursor.

Per Gmail's documented pagination pattern, the correct approach is to keep following
`nextPageToken` until it's exhausted and only then treat the final response's `historyId` as
the new checkpoint. Skipping that loop means:
- If `historyId` is only populated on the true last page, the field comes back empty and the
  cursor doesn't advance — not lossy, just wasteful (re-fetches/dedupes the same page next poll).
- If `historyId` instead reflects the mailbox's current state regardless of pagination, the
  cursor jumps straight past the unfetched remaining pages — **those messages are then
  permanently skipped**, since the next poll starts from the already-advanced cursor.

**When it bites:** only when more new mail accumulates during a sync gap (e.g. extended
downtime) than one page (`maxResults=50`) can cover in a single sync cycle.

**Fix:** loop on `nextPageToken` in `fetchViaHistory()` until absent, accumulating
`messagesAdded` across all pages, then take `historyId` from the final page's response.

**Status:** not yet fixed — flagged during live pipeline investigation on 2026-11-10, same day
as the Gmail attachment-loss fix and the PO-Processor non-PO-email filter fix. Scoped
separately since it requires its own test coverage (simulating a multi-page history response).

## UMIS ingestion never repairs/updates an already-stored duplicate email

**File:** `backend/src/main/java/com/umis/email/service/EmailIngestionService.java`,
`processNormalizedEmail()` (lines 60-68)

**Issue:** The dedup check is identity-only (`provider_message_id` match against
`email_messages`) and returns early via `IngestionResult.duplicate(existing)` the moment a
match is found — it never re-parses the incoming payload or compares/updates anything on the
existing row. `content_hash` (`EmailMessage.java:82`) looks like it could support
change-detection but is write-only: computed once at creation
(`sha256(connectionId + ":" + providerMessageId + ":" + subject)`, no attachment data in it)
and never read back or compared anywhere.

**Consequence:** any email ingested once — in particular, one ingested *before* the Gmail
attachment-loss fix landed, so it was stored with zero attachments — stays exactly as first
ingested forever. A later resync of the same message (now correctly carrying its attachment
under the fixed code) is dropped by the dedup check before attachment logic ever runs, so the
existing attachment-less record is never repaired. PO-Processor never even sees a second
`EMAIL_INGESTED` event for it, since `createOutboxEvent()` is upstream of the dedup check.

**Fix (not yet scoped/built):** some opt-in backfill/repair path — e.g. a script or admin
endpoint that re-fetches a message by `provider_message_id`, bypassing (or explicitly
overriding) the identity-dedup check, and patches the existing `email_messages`/
`email_attachments` rows plus republishes `EMAIL_INGESTED` so PO-Processor can pick up the
now-available attachment — without creating a duplicate `email_messages` row.

**Status:** flagged 2026-11-10, same investigation session as the pagination gap above. This
is the "resync/backfill mechanism for already-ingested-without-attachment emails" that was
in the original attachment-bug scope but was explicitly deprioritized when the user redirected
to the non-PO-filtering issue.

## UMIS ↔ PO-Processor service-to-service trust model is weak

**Files:**
- `PO-Processor/src/main/java/com/po/processor/service/UmisClient.java` (JWT acquisition/attach)
- `backend/src/main/java/com/umis/event/consumer/controller/AcknowledgementController.java`
- `backend/src/main/java/com/umis/event/consumer/service/ConsumerService.java`
- `backend/src/main/java/com/umis/config/RabbitMqConfig.java` /
  `PO-Processor/src/main/java/com/po/processor/config/RabbitMqConfig.java`
- `backend/src/main/java/com/umis/event/consumer/model/ConsumerRegistration.java`

**Issue — queue side:** UMIS and PO-Processor connect to the *same* RabbitMQ broker with the
*same* `guest`/`guest` dev credentials (per `PO-Processor/.env.example`). There is no
message-level signing (no HMAC/JWT embedded in the event payload) — the only "authentication"
for consuming `EMAIL_INGESTED` events is the ability to open a broker connection. A
`consumer_registrations` table (`ConsumerRegistration` entity, tracking `consumerCode`/
`subscribedEvents`) exists but is never queried anywhere in the publish or consume path — it's
a dead registry, not an enforcement mechanism.

**Issue — HTTP callback side:** `UmisClient.getOrFetchJwtToken()` obtains its bearer token by
logging into UMIS's ordinary human-user login endpoint (`POST /api/v1/auth/login`) with a
configured email+password (default `admin@umis.local` + a plaintext default password in
`.env.example`) — not a dedicated service-account/API-key mechanism. The receiving endpoint,
`AcknowledgementController`, only requires `@PreAuthorize(HAS_TENANT_ACCESS)` — the same
generic role check (`GLOBAL_ADMIN`/`TENANT_ADMIN`/`TENANT_VIEWER`) a browser user would need; a
`SERVICE_CLIENT` role exists in `RoleConstants` but isn't used here. `ConsumerService
.acknowledgeProcessing()` never validates the caller's identity against `consumer_registrations`
— it just trusts whatever string is in the request body's `consumerModule` field.

**Net effect:** PO-Processor isn't cryptographically distinguished as a distinct service — it
authenticates to RabbitMQ with shared broker credentials and to UMIS's HTTP API by holding a
real admin account's password. Anything else that could reach the broker or obtain a
`TENANT_VIEWER`+ JWT could equally subscribe to events or post acknowledgements as any
`consumerModule` it likes.

**Fix (not yet scoped/built):** issue PO-Processor a dedicated `SERVICE_CLIENT`-scoped
credential (JWT or API key) distinct from any human admin login, and have
`ConsumerService.acknowledgeProcessing()` actually validate the caller against
`consumer_registrations` rather than trusting the request body's `consumerModule`. Lower
priority for the current single-deployment, trusted-network setup, but worth tightening before
any broader/less-trusted deployment.

**Status:** flagged 2026-11-10, same investigation session as the two items above —
informational for now, not actioned.
