# UMIS Deployment & Infrastructure Guide

## 1. Local Development with Docker Compose

UMIS includes a pre-configured Docker Compose cluster containing all required services:
- **PostgreSQL 16**: Port 5432
- **RabbitMQ 3.13**: AMQP Port 5672, Management UI Port 15672
- **MinIO S3**: S3 API Port 9000, Console Port 9001
- **Backend Service**: Port 8080
- **Frontend Service**: Port 3000

### Quick Start:
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Launch the infrastructure stack
docker-compose -f docker/docker-compose.yml up -d
```

Access Points:
- Frontend Dashboard: `http://localhost:3000`
- Backend API / Swagger UI: `http://localhost:8080/swagger-ui.html`
- RabbitMQ Management Console: `http://localhost:15672` (User: `guest` / `guest`)
- MinIO Web Console: `http://localhost:9001` (User: `minioadmin` / `minioadmin`)

## 2. Production Deployment Architecture

```
Internet / Corporate Traffic
             |
             v
+---------------------------+
| TLS Ingress Reverse Proxy |
| (Nginx / Traefik / AWS ALB)|
+-------------+-------------+
              |
              +--------------------------+
              | /api/*                   | /*
              v                          v
    +-------------------+      +-------------------+
    | UMIS Backend      |      | UMIS Frontend     |
    | (Spring Boot 3)   |      | (Nginx Alpine)    |
    | Replicas: 2+      |      | Replicas: 2+      |
    +---------+---------+      +-------------------+
              |
              +--------------------------+--------------------------+
              |                          |                          |
              v                          v                          v
    +-------------------+      +-------------------+      +-------------------+
    | Managed PostgreSQL|      | Managed RabbitMQ  |      | S3 Object Storage |
    | (Amazon RDS /     |      | (CloudAMQP /      |      | (AWS S3 /         |
    |  Azure Flexible)  |      |  Amazon MQ)       |      |  MinIO Cluster)   |
    +-------------------+      +-------------------+      +-------------------+
```

### Production Hardening Guidelines
1. **Database**: Use managed PostgreSQL with automated daily snapshots, read replicas, and SSL enforcement.
2. **Secrets**: Inject JWT secrets, AES-256 encryption keys, and OAuth client credentials via AWS Secrets Manager or HashiCorp Vault.
3. **No Public Storage/Broker Ports**: Ensure PostgreSQL (5432) and RabbitMQ AMQP (5672) are strictly bound to private subnets.
4. **Horizontal Scaling**: Ingestion workers use database locks (`sync_lock_token`) to coordinate synchronization safely across multiple backend replicas without duplicate ingestion jobs.
