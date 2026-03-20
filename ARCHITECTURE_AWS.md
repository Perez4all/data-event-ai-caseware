# AWS Architecture — Production Scale

Three approaches to migrate the local Docker Compose solution to AWS at production NFRs (5,000 tenants, 250M total records, 5,000 updates/sec peak, 300–1,000 QPS search).

---

## Option A — Custom App on AWS (Lift & Shift)

Keeps the existing Spring Boot ingestion service and Python ranker, deployed as containers on AWS with managed infrastructure underneath.

### Services

| Layer | AWS Service | Replaces (local) | Why this service |
|---|---|---|---|
| **Database** | RDS PostgreSQL Multi-AZ | Docker PostgreSQL | Same engine, automatic failover, read replicas for ingestion reads. No migration effort. |
| **Ingestion** | ECS Fargate (scheduled every 30–60s) | Spring Boot container | Already Dockerized. Fargate = serverless containers, no EC2 management. Scheduled task replaces manual `POST /ingest`. |
| **Checkpoint** | RDS PostgreSQL (`checkpoints` table) | `state/checkpoint.json` | One row in the DB you already have. No extra service needed. |
| **Data Lake** | S3 (versioned, SSE-KMS) | `./lake/` filesystem | 11 nines durability, versioning for immutability, lifecycle policies for GDPR retention/deletion. |
| **Events/Audit** | S3 (one JSONL per run) | `events/events.jsonl` | Append-only audit trail, no extra service. |
| **Search** | OpenSearch Service | Python TF-IDF ranker | 200M cases at 1,000 QPS burst with p95 < 300ms cannot be served by in-memory TF-IDF. OpenSearch provides BM25 ranking, horizontal sharding, replicas. |
| **API Gateway** | ALB + ECS Fargate | `localhost:8080` / `localhost:8000` | Load balancing, TLS termination, health checks. Single ALB routes `/ingest` → Java service, `/search` → OpenSearch proxy. |
| **Monitoring** | CloudWatch | Docker logs | Native metrics for all AWS services. Custom alarms on ingestion lag, search latency, error rates. |

### How it meets NFRs

| NFR | How |
|---|---|
| **Freshness ≤ 10 min** | ECS scheduled task runs every 30–60s. Poll-based `WHERE updated_at > checkpoint` on read replica. Worst case: 60s poll + query + S3 write + OpenSearch index ≈ 2–3 min. |
| **Search 1,000 QPS, p95 < 300ms** | OpenSearch cluster with 3 data nodes + 2 replicas. Index sharded by tenant prefix. |
| **Availability 99.9%** | RDS Multi-AZ (automatic failover), ECS service auto-restart, OpenSearch Multi-AZ. ALB health checks remove unhealthy tasks. |
| **RPO ≤ 5 min** | RDS automated backups (5-min intervals). S3 versioning = no data loss. OpenSearch snapshots every 1 hour (re-indexable from lake). |
| **RTO ≤ 30 min** | RDS failover < 2 min. ECS tasks restart in < 1 min. OpenSearch recovers from replica. |
| **Security** | SSE-KMS encryption at rest (S3, RDS, OpenSearch). TLS in transit. IAM roles per ECS task (least privilege). CloudTrail for audit logging. VPC + security groups isolate services. |
| **GDPR** | S3 lifecycle rules for retention periods. Delete tenant data via S3 prefix deletion + RDS `DELETE`. OpenSearch index-per-tenant allows clean deletion. |
| **Cost** | ~$700–1,200/month (RDS ~$300, OpenSearch ~$300, Fargate ~$100, S3 ~$10, CloudWatch ~$30). |

### Idempotency & Failure Handling

- **Checkpoint in DB** with conditional update (`UPDATE ... WHERE checkpoint = old_value`) prevents double-processing.
- **S3 PutObject** with same key overwrites atomically — same data written twice = same result.
- **OpenSearch upsert** by `case_id` — re-indexing same document is idempotent.
- **Replay:** Reset checkpoint to any past timestamp → re-ingests from that point. Lake overwrites, OpenSearch upserts. No duplicates.

### Tradeoffs

| Pro | Con |
|---|---|
| Full control over ingestion logic, schema validation, fingerprinting | You maintain and operate the ingestion code |
| JSONL format matches current local setup | Parquet would be more efficient for analytics at scale |
| Minimal migration from Docker Compose | Poll-based ingestion adds RDS read load at high frequency |
| Any custom business logic in Java | Need to build multi-tenant partitioning yourself |

---

## Option B — AWS Lake Formation (Managed)

Replaces the custom ingestion pipeline with AWS's purpose-built managed data lake service. The Spring Boot app is eliminated for ingestion; only a thin API layer remains.

### Services

| Layer | AWS Service | Replaces | Why this service |
|---|---|---|---|
| **Database** | RDS PostgreSQL Multi-AZ | Docker PostgreSQL | Same as Option A. |
| **Ingestion + CDC** | Lake Formation + Glue | Spring Boot ingestion service, LakeHandler, checkpoint.json | Pre-built CDC blueprints connect directly to RDS. Automatic incremental ingestion, partitioning, bookmarking. No custom code needed. |
| **Schema Registry** | Glue Data Catalog | Schema fingerprint validation | Central schema registry with drift detection. Replaces `HashUtil.schemaFingerprint()`. |
| **Data Lake** | S3 (managed by Lake Formation) | `./lake/` filesystem | Same S3 benefits as Option A, but Lake Formation manages the write path, partitioning, and catalog registration automatically. |
| **Data Format** | Parquet (columnar, compressed) | JSONL | Lake Formation writes Parquet by default. 5–10x smaller than JSONL, columnar for analytics. |
| **Multi-tenant Access** | Lake Formation Permissions | Not implemented locally | Row-level and column-level security per tenant. Tag-based access control. Built-in for 5,000 tenants. |
| **Search** | OpenSearch Service | Python TF-IDF ranker | Same as Option A. Fed from S3 via Glue ETL job or Lambda triggered on new S3 objects. |
| **API Layer** | API Gateway + Lambda | Spring Boot `/ingest` endpoint | Thin trigger: Lambda calls Glue workflow start. `/search` proxies to OpenSearch. |
| **Events/Audit** | CloudTrail + S3 | `events/events.jsonl` | Lake Formation actions are automatically logged by CloudTrail. No custom event file needed. |
| **Monitoring** | CloudWatch | Docker logs | Glue job metrics, Lake Formation access logs, OpenSearch dashboards. |

### How it meets NFRs

| NFR | How |
|---|---|
| **Freshness ≤ 10 min** | Glue streaming ETL (micro-batch every 1–5 min) or scheduled Glue job every 5 min. CDC captures changes from WAL — no polling delay. |
| **Search 1,000 QPS, p95 < 300ms** | Same OpenSearch cluster as Option A. Glue job indexes new Parquet files into OpenSearch after each run. |
| **Availability 99.9%** | All managed services (RDS, Lake Formation, Glue, OpenSearch) are Multi-AZ by default. API Gateway has built-in HA. |
| **RPO ≤ 5 min** | CDC from WAL captures every committed transaction. S3 versioning on lake. Glue bookmark tracks exact position. |
| **RTO ≤ 30 min** | No custom app to restart. Glue jobs auto-retry on failure. OpenSearch recovers from replicas. |
| **Security** | Lake Formation provides centralized permission management. KMS encryption, VPC endpoints, CloudTrail audit. PII columns can be tagged and access-controlled per tenant. |
| **GDPR** | Lake Formation tag-based governance. Delete tenant's data: revoke permissions + delete S3 prefix + drop Glue catalog partition. |
| **Cost** | ~$500–1,000/month (RDS ~$300, Glue ETL ~$50–150 based on DPU-hours, OpenSearch ~$300, S3 ~$10, Lambda ~$5). Lower operational cost — no ingestion app to maintain. |

### Idempotency & Failure Handling

- **Glue bookmark** tracks exactly which records have been processed. Built-in — no checkpoint file needed.
- **Glue job retry** with bookmark ensures idempotent re-processing on failure.
- **S3 overwrite** per partition — same data written to same partition path = same result.
- **Replay:** Reset Glue bookmark → re-processes from any point. Lake Formation handles catalog updates.

### Tradeoffs

| Pro | Con |
|---|---|
| No ingestion code to write or maintain | Less control over exact output format (Parquet vs JSONL) |
| Built-in multi-tenant access control | Glue ETL has cold start latency (~1 min for job init) |
| Automatic schema management and drift detection | Vendor lock-in to AWS Lake Formation ecosystem |
| CDC via WAL — no database polling load | Learning curve for Glue ETL and Lake Formation permissions |
| Lower operational burden at scale | Glue costs scale with data volume (DPU-hours) |
| Parquet format enables future analytics (Athena, Redshift Spectrum) | Cannot easily run locally — development requires AWS sandbox |

---

## Option C — Hardened Custom App + Kinesis (Streaming)

Evolves Option A to fix its critical weaknesses at scale — multi-tenant isolation, GDPR deletion, poll-based backpressure, and JSONL inefficiency — without adopting Lake Formation. Replaces polling with CDC streaming via DMS + Kinesis and re-partitions the lake by tenant.

### What Option C fixes from Option A

| Option A Problem | Severity | How Option C fixes it |
|---|---|---|
| **Multi-tenant isolation** (5,000 IAM policies unsustainable) | Critical | Single IAM role + app-layer tenant isolation. S3 partitioned by `tenant={id}/dt=yyyy-MM-dd/`. Tenant context from JWT. No per-tenant IAM policies. |
| **GDPR tenant deletion** (rewrite every date-partitioned file) | Critical | Tenant-first S3 partitioning → `aws s3 rm s3://lake/cases/tenant=42/ --recursive`. One command, no file rewriting. |
| **Peak 5,000 updates/sec** (poll-based backpressure, 300K row batches) | High | DMS reads PostgreSQL WAL → Kinesis stream. No polling queries. Continuous flow of small records instead of large batches. |
| **Tenant-scoped search** (app-code filtering = data leak risk) | High | OpenSearch Document-Level Security (DLS) enforces `tenant_id` filter on every query. Cannot be bypassed even with app bugs. |
| **JSONL at 200M scale** (bloated, not analytics-friendly) | Medium | Firehose auto-converts JSON → Parquet. 5-10x storage reduction, columnar reads for analytics. |
| **SOC2 audit nightmare** (no centralized permission catalog) | Medium | `tenant_permissions` table in RDS + CloudTrail. One IAM role to audit, tenant isolation proven via app tests + DLS. |

### Services

| Layer | AWS Service | Replaces (vs Option A) | Why this service |
|---|---|---|---|
| **Database** | RDS PostgreSQL Multi-AZ | Same | Same engine, automatic failover. |
| **CDC** | DMS (Database Migration Service) | ECS scheduled polling | Reads PostgreSQL WAL, streams every committed change to Kinesis. Zero query load on RDS. |
| **Stream** | Kinesis Data Streams (6 shards) | Checkpoint-based batch queries | 1 shard = 1,000 writes/sec. 6 shards handles 5,000+ updates/sec. 24h retention as buffer. |
| **Lake Writer** | Kinesis Data Firehose | Java `LakeHandler.writeToDataLake()` | Auto-converts JSON → Parquet, writes tenant-partitioned files to S3. No custom write code. |
| **Data Lake** | S3 (versioned, SSE-KMS, Parquet) | S3 (JSONL, date-only partitioning) | Tenant-first partitioning: `s3://lake/cases/tenant={id}/dt=yyyy-MM-dd/`. Parquet format. |
| **Events/Audit** | S3 (one JSONL per run) | Same | Audit trail unchanged. |
| **Search Indexer** | ECS Fargate (Kinesis consumer) | Same ECS task that wrote to lake | Reads from Kinesis stream, bulk-upserts to OpenSearch by `case_id`/`customer_id`. |
| **Search** | OpenSearch Service + DLS | OpenSearch (no DLS) | Document-Level Security enforces `tenant_id` filter per JWT. Cross-tenant leaks impossible. |
| **API Gateway** | ALB + ECS Fargate | Same | `/search` routes to search proxy. `/ingest` no longer needed (CDC is continuous). |
| **Tenant Permissions** | RDS (`tenant_permissions` table) | Not present in Option A | Centralized permission registry for SOC2 audit. |
| **Monitoring** | CloudWatch | Same | DMS replication lag, Kinesis iterator age, Firehose delivery errors, OpenSearch metrics. |
| **Security** | KMS + Secrets Manager + IAM + CloudTrail | Same | Single task role with least privilege. Credentials in Secrets Manager. |
| **Container Registry** | ECR | Same | Images for search proxy and Kinesis consumer. |

### How it meets NFRs

| NFR | How |
|---|---|
| **Freshness ≤ 10 min** | CDC latency ~1-2s + Firehose buffer (1-5 min configurable) ≈ **1-6 min end-to-end**. Better than Option A's 2-3 min, configurable tradeoff between latency and S3 file size. |
| **Search 1,000 QPS, p95 < 300ms** | Same OpenSearch cluster as A/B. DLS adds negligible overhead (~1ms per query for term filter). |
| **Availability 99.9%** | RDS Multi-AZ, Kinesis is Multi-AZ by default, OpenSearch Multi-AZ. DMS auto-restart on failure. ECS auto-restart. |
| **RPO ≤ 5 min** | CDC captures every WAL commit. Kinesis retains 24h. S3 versioning. No data loss window. |
| **RTO ≤ 30 min** | DMS auto-resumes from last WAL position. Kinesis consumer resumes from last checkpoint (KCL). OpenSearch from replicas. |
| **Security** | Single IAM role (auditable). OpenSearch DLS per tenant. KMS encryption everywhere. Secrets Manager for credentials. CloudTrail for all API calls. |
| **GDPR** | Tenant-first S3 partitioning → one `aws s3 rm` per tenant. OpenSearch `DELETE BY QUERY` where `tenant_id = X`. RDS `DELETE FROM ... WHERE tenant_id = X`. |
| **Multi-tenancy (5,000)** | App-layer isolation (JWT tenant context) + OpenSearch DLS. No per-tenant IAM. `tenant_permissions` table for audit catalog. |
| **Cost** | ~$886/month (RDS ~$300, OpenSearch ~$300, DMS ~$75, Kinesis ~$50, Firehose ~$6, ECS ~$100, S3 ~$5, other ~$50). |

### Idempotency & Failure Handling

- **Kinesis sequence number** replaces timestamp-based checkpoint. KCL (Kinesis Client Library) manages exactly-once consumption tracking.
- **DMS restart** resumes from the last WAL LSN (Log Sequence Number). No data loss, no duplicates.
- **Firehose** delivers at-least-once to S3. Same Parquet partition key = same file overwritten. Idempotent.
- **OpenSearch upsert** by document ID (`case_id`/`customer_id`). Re-indexing same record = same result.
- **Replay:** Stop DMS, reset Kinesis stream (or create new stream), restart DMS from a specific WAL LSN. Lake overwrites, OpenSearch upserts. No duplicates.
- **Backpressure:** If consumer falls behind, Kinesis retains data for 24h (extendable to 7 days). No data loss, consumer catches up naturally.

### Tradeoffs

| Pro | Con |
|---|---|
| Fixes all 6 critical/high issues from Option A | 3 more AWS services to manage (DMS, Kinesis, Firehose) |
| Real-time CDC — no database polling load | DMS replication instance is always-on (~$75/month even at idle) |
| Parquet format — 5-10x storage savings + analytics-ready | Cannot easily run CDC pipeline locally (Docker Compose stays poll-based for dev) |
| Tenant-first partitioning — instant GDPR deletion | Slightly higher infrastructure complexity vs vanilla Option A |
| OpenSearch DLS — zero-trust tenant isolation | Firehose buffer adds 1-5 min latency to lake writes (configurable) |
| Full control over business logic (still your Java/Spring code) | Team needs to learn Kinesis/DMS operational patterns |
| No Lake Formation vendor lock-in | No centralized schema registry (still use app-level fingerprint validation) |

### Top 3 Cost Drivers & Optimization

| # | Cost Driver | Current | Optimization |
|---|---|---|---|
| 1 | **RDS PostgreSQL Multi-AZ** | ~$300/month | Reserved instances (1-year commit = ~40% savings). Right-size instance class after load testing. |
| 2 | **OpenSearch Service** | ~$300/month | Reserved instances. Start with 2 data nodes, scale to 3 only if QPS demands. Use UltraWarm for older indices. |
| 3 | **DMS + Kinesis** | ~$125/month | Use smallest DMS instance (`dms.t3.medium`). Reduce Kinesis shard count during off-peak via auto-scaling. |

---

## Comparison Summary

| Dimension | Option A (Custom App) | Option B (Lake Formation) | Option C (Hardened + Kinesis) |
|---|---|---|---|
| **Migration effort** | Low — Docker → Fargate | Medium — rewrite ingestion as Glue jobs | Medium — add DMS/Kinesis, re-partition S3, add DLS |
| **Operational burden** | Medium — maintain ingestion code | Low — managed by AWS | Medium — maintain consumer code + DMS/Kinesis |
| **Control** | Full — custom logic, format, timing | Limited — Glue/Lake Formation conventions | Full — custom logic with streaming primitives |
| **Ingestion model** | Poll-based (batch every 30-60s) | CDC via Glue (managed) | CDC via DMS + Kinesis (streaming) |
| **Data format** | JSONL | Parquet (auto) | Parquet (via Firehose) |
| **Multi-tenancy** | Manual (S3 prefix + IAM) — breaks at 5,000 | Built-in (tag-based row/column security) | App-layer + OpenSearch DLS — works at 5,000 |
| **GDPR deletion** | Rewrite every date-partitioned file | S3 prefix + Lake Formation revoke | `aws s3 rm` by tenant prefix — instant |
| **Peak ingestion** | 300K row batches, backpressure risk | CDC via WAL, managed | CDC via WAL + Kinesis buffer, no backpressure |
| **Local dev experience** | Docker Compose works unchanged | Cannot run locally | Docker Compose for dev (poll-based), CDC only in AWS |
| **Vendor lock-in** | Low (S3 + RDS are portable) | High (Lake Formation + Glue) | Medium (Kinesis/DMS are AWS-specific, but replaceable) |
| **Cost (monthly)** | ~$700–1,200 | ~$500–1,000 | ~$886 |
| **Best for** | Small tenant count, quick launch | Greenfield data lakes, minimal ops | Production SaaS at 5,000 tenants with full control |

### Recommendation

**Option A** for proof-of-concept or single-tenant deployments — minimal risk, direct migration from Docker Compose.

**Option C** for production SaaS launch — fixes all critical NFR gaps from Option A (multi-tenancy, GDPR, peak ingestion) while keeping full control over business logic. No Lake Formation lock-in.

**Option B** when the team prioritizes minimal operational burden over control, or when data volume exceeds 1B records and centralized governance (Lake Formation tags) justifies the ecosystem lock-in.

---

> Diagrams: see [ARCHITECTURE_AWS_DIAGRAMS.md](ARCHITECTURE_AWS_DIAGRAMS.md)
