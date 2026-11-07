# UMIS – Failure Modes and Recovery Test Report

## 1. Executive Summary

This report documents the failure recovery testing conducted on UMIS to ensure zero data loss, guaranteed
at-least-once event delivery, concurrent worker safety, and resilient third-party recovery.

All failure scenarios have automated test coverage verified via Maven:
`mvn test -Dtest=SynchronizationReliabilityTest,MessagingAndOutboxTest`

---

## 2. Transactional Outbox Failure & Atomic Rollback

### 2.1. Scenario: Outbox Event Persistence Failure
- **Trigger**: During email ingestion, storage of raw email and attachments succeeds, but outbox event
  persistence fails due to serialization or database constraint error.
- **Expected Behavior**: The entire transaction rolls back. Neither the ingested email, attachments,
  nor partial outbox records remain committed in PostgreSQL.
- **Verification**: In [EmailIngestionService.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/service/EmailIngestionService.java),
  any failure during `createOutboxEvent` triggers an unchecked exception.
- **Test Reference**: `MessagingAndOutboxTest.testOutboxPersistenceFailureRollsBackEntireTransaction()` (PASS).

---

## 3. RabbitMQ Failure & Outbox Relay Retry Backoff

### 3.1. Scenario: RabbitMQ Broker Unavailable
- **Trigger**: RabbitMQ is temporarily unreachable during the outbox relay cycle.
- **Expected Behavior**:
  1. Events remain safely persisted in `t_event_outbox` with status `PENDING`.
  2. The relay increments `retry_count` and records the error message.
  3. [OutboxRelayScheduler.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/event/publisher/OutboxRelayScheduler.java)
     applies exponential backoff.
  4. Once RabbitMQ recovers, pending events are published in strict sequence.
- **Test Reference**: `MessagingAndOutboxTest.testOutboxRelayRetriesOnRabbitMQFailure()` (PASS).

---

## 4. Mailbox Concurrency & Atomic Row Locking

### 4.1. Scenario: Multiple Workers Synchronizing Same Mailbox
- **Trigger**: In a multi-replica deployment, two background workers attempt to poll the same mailbox simultaneously.
- **Expected Behavior**: Only one worker acquires the lock. The second worker skips execution cleanly without
  duplicate ingestion or cursor race conditions.
- **Implementation**: [MailboxSyncStateRepository.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/repository/MailboxSyncStateRepository.java)
  executes an atomic conditional update:
  `UPDATE t_mailbox_sync_state SET sync_lock_token = :token WHERE mailbox_id = :id AND sync_lock_token IS NULL`
- **Test Reference**: `SynchronizationReliabilityTest.testMutualExclusionDuringSync()` (PASS).

### 4.2. Scenario: Worker Crash / Stale Lock Recovery
- **Trigger**: A worker acquires a sync lock and crashes before releasing it.
- **Expected Behavior**: The lock has a 15-minute TTL. Subsequent workers recognize the expired timestamp,
  override the lock, and resume mailbox polling without operator intervention.
- **Test Reference**: `SynchronizationReliabilityTest.testExpiredLockRecovery()` (PASS).

---

## 5. Third-Party Provider Expired Cursor Recovery

### 5.1. Scenario: Microsoft Graph Delta Token Expired (HTTP 410)
- **Trigger**: Delta link is older than 30 days or revoked by Microsoft. Graph API returns HTTP 410 with
  code `resyncRequired`.
- **Expected Behavior**:
  1. [MicrosoftGraphConnector.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/connector/impl/MicrosoftGraphConnector.java)
     detects 410 and flags cursor expired.
  2. [EmailSyncManager.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/email/service/EmailSyncManager.java)
     clears `cursor_value` in `t_mailbox_sync_state`.
  3. Performs an immediate full baseline sync and stores the fresh delta link.
- **Test Reference**: `SynchronizationReliabilityTest.testExpiredCursorRecovery()` (PASS).

---

## 6. Consumer Idempotency & Multi-Consumer Independent Status

### 6.1. Scenario: Duplicate Consumer Acknowledgements
- **Trigger**: A downstream consumer processes an email and acknowledges twice due to RabbitMQ redelivery.
- **Expected Behavior**: The duplicate acknowledgement is handled idempotently without error.
- **Test Reference**: `MessagingAndOutboxTest.testConsumerAcknowledgementIsIdempotent()` (PASS).

### 6.2. Scenario: Multi-Consumer Independent Status Tracking
- **Trigger**: Two downstream consumers (`PO_PROCESSING` and `INVOICE_PROCESSING`) consume the same email.
- **Expected Behavior**: Each consumer has an independent status record in `t_consumer_acknowledgement`.
  If one fails and one succeeds, their independent statuses are accurately preserved.
- **Test Reference**: `MessagingAndOutboxTest.testIndependentConsumerStatusesForSameEmail()` (PASS).
