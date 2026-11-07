# UMIS – Production Deployment Readiness Guide

## 1. Executive Summary

This guide outlines the production deployment topology, high-availability architecture, persistence,
observability, and backup/recovery procedures required to run the Universal Mailbox Integration Service (UMIS)
at scale across multiple tenants.

---

## 2. Target Production Architecture

```
                       +-----------------------------------+
                       |    Cloudflare / AWS ALB (TLS 1.3) |
                       +-----------------+-----------------+
                                         |
                       +-----------------v-----------------+
                       |      Kubernetes Ingress / Nginx   |
                       +-----------------+-----------------+
                                         |
                 +-----------------------+-----------------------+
                 |                                               |
        +--------v--------+                             +--------v--------+
        | React Frontend  |                             |   UMIS Backend  |
        | (Vite SPA CDN)  |                             | (3+ Pod Replicas|
        +-----------------+                             +--------+--------+
                                                                 |
              +--------------------------+-----------------------+-----------------------+
              |                          |                       |                       |
     +--------v--------+        +--------v--------+     +--------v--------+     +--------v--------+
     | PostgreSQL HA   |        | RabbitMQ Cluster|     | S3 / MinIO Clust|     | Prometheus /    |
     | (RDS Multi-AZ / |        | (3 Nodes, Quorum|     | (Distributed,   |     | Grafana /       |
     |  Patroni Cluster|        |  Queues & DLQ)  |     |  SSE-KMS)       |     | OpenTelemetry   |
     +-----------------+        +-----------------+     +-----------------+     +-----------------+
```

### 2.1. Stateless Backend Replicas
- The Spring Boot backend is completely stateless; session state is managed via signed JWT tokens.
- Mailbox polling concurrency is safely coordinated across any number of replicas via atomic database row
  locks in `t_mailbox_sync_state` ([MailboxSyncStateRepository.java](file:///D:/Ashish_P/Project%20X/UMIS/backend/src/main/java/com/umis/mailbox/repository/MailboxSyncStateRepository.java)).
- Zero inter-pod communication is required for mailbox sync or outbox relaying.

---

## 3. Storage & Infrastructure Hardening

### 3.1. PostgreSQL High Availability
- **Engine**: PostgreSQL 16+ on AWS RDS Multi-AZ or Patroni HA cluster.
- **Connection Pool**: HikariCP configured with:
  - `maximum-pool-size: 25` per replica
  - `minimum-idle: 10`
  - `idle-timeout: 300000`
  - `connection-timeout: 20000`
- **Data Partitions**: Tenant IDs are indexed across all operational tables:
  `t_tenant`, `t_mailbox_connection`, `t_raw_email`, `t_email_attachment`, `t_event_outbox`.

### 3.2. S3 Object Storage
- **Bucket Hierarchy**: `emails/{tenantId}/{mailboxId}/{emailId}/{filename}`.
- **Encryption**: Server-Side Encryption with Customer Master Keys (`SSE-KMS`).
- **Retention / Lifecycle**: Hot tier (S3 Standard) for 90 days, transitioning to S3 Glacier Flexible Retrieval.

### 3.3. RabbitMQ High Availability
- **Queue Type**: Quorum queues (`x-queue-type: quorum`) on a 3-node cluster.
- **Dead Lettering**: All event exchanges bind to `umis.dead-letter.exchange` routing to `umis.dlq`.
- **Publisher Confirms**: Enabled in Spring AMQP to guarantee delivery before updating outbox status.

---

## 4. Disaster Recovery & Backup Runbook

| Component | Backup Frequency | Method / Tool | RPO | RTO |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL** | Continuous WAL + Daily Snapshot | AWS RDS Automated Backup / pgBackRest | < 5 min | < 30 min |
| **S3 Storage** | Continuous | S3 Cross-Region Replication (CRR) + Versioning | < 1 min | < 15 min |
| **Secrets / Keys**| On rotation | AWS Secrets Manager / Azure Key Vault | 0 | < 5 min |

---

## 5. Observability & Alerting Policies

### 5.1. Health Endpoints
- **Liveness**: `GET /actuator/health/liveness` (responds HTTP 200 while JVM is running).
- **Readiness**: `GET /actuator/health/readiness` (verifies PostgreSQL, RabbitMQ, and S3 connectivity).
- **Metrics**: `GET /actuator/prometheus` (scraped every 15s).

### 5.2. Critical Production Alerts
1. **Outbox Relay Backpressure**:
   - `sum(umis_outbox_pending_events) > 1000` for 5 minutes.
   - Severity: CRITICAL. Indicates RabbitMQ broker failure or network partition.
2. **Mailbox Sync Failure Rate**:
   - `rate(umis_mailbox_sync_failures_total[10m]) > 0.05`.
   - Severity: WARNING. Indicates revoked OAuth tokens or provider API throttling.
3. **Database Concurrency Lock Contention**:
   - `sum(umis_mailbox_lock_held_seconds > 600)`.
   - Severity: WARNING. Worker node may have stalled or mailbox contains oversized delta payload.
