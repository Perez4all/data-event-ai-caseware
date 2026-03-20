# Execution Plan — Option A: Custom App on AWS

Migration from Docker Compose to AWS (ECS Fargate + RDS + S3 + OpenSearch).

---

## Phase 0 — Pre-requisites & Foundation (Week 1)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 0.1 | Create AWS account / org unit for the project | Infra | AWS account with billing alerts |
| 0.2 | Set up Terraform or CDK project for IaC | Infra | Git repo with IaC scaffold, remote state in S3 + DynamoDB lock |
| 0.3 | Define VPC: 2 AZs, public/private subnets, NAT gateway | Infra | VPC deployed, subnets tagged |
| 0.4 | Create ECR repositories (`caseware-ingestion`, `caseware-ranker`) | Infra | ECR repos with lifecycle policies (keep last 10 images) |
| 0.5 | Create KMS key for encryption (S3, RDS, OpenSearch) | Infra | KMS key ARN, alias `caseware-key` |
| 0.6 | Enable CloudTrail for the account | Infra | CloudTrail logging to S3 |
| 0.7 | Create IAM roles: `ecs-task-execution-role`, `ecs-task-role` | Infra | Roles with least-privilege policies |

**Exit criteria:** VPC running, ECR repos exist, KMS key created, IAM roles defined.

---

## Phase 1 — Database (Week 2)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 1.1 | Provision RDS PostgreSQL 16, Multi-AZ, private subnet | Infra | RDS endpoint, encrypted at rest (KMS) |
| 1.2 | Create read replica in second AZ | Infra | Read replica endpoint |
| 1.3 | Run `db/init.sql` to create schema (customers, cases tables) | Backend | Schema deployed |
| 1.4 | Add `checkpoints` table to RDS (`ALTER` or new migration) | Backend | `checkpoints` table with `key VARCHAR PRIMARY KEY, value TIMESTAMPTZ, updated_at TIMESTAMPTZ` |
| 1.5 | Migrate checkpoint logic from file-based to DB-based | Backend | `LakeHandler.readCheckPoint()` / `writeCheckPoint()` use JDBC instead of file I/O |
| 1.6 | Configure automated backups: 7-day retention, 5-min RPO | Infra | Backup policy verified |
| 1.7 | Set up security group: allow port 5432 only from ECS task SG | Infra | SG rules applied |

**Exit criteria:** RDS accessible from private subnet, checkpoint reads/writes work against DB, backups configured.

---

## Phase 2 — S3 Data Lake & Events (Week 2–3)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 2.1 | Create S3 bucket `caseware-lake` with versioning + SSE-KMS | Infra | Bucket with versioning enabled |
| 2.2 | Create S3 bucket `caseware-events` with SSE-KMS | Infra | Bucket for audit trail |
| 2.3 | Configure lifecycle rules: transition to IA after 90 days, Glacier after 365 days | Infra | Lifecycle policy attached |
| 2.4 | Adapt `LakeHandler.writeToDataLake()` to write to S3 instead of local filesystem | Backend | `S3Client.putObject()` replaces `Files.writeString()` |
| 2.5 | Adapt `LakeHandler.writeEvents()` to write to S3 (`s3://caseware-events/run-{runId}.jsonl`) | Backend | Each run writes its own event file to S3 |
| 2.6 | Remove local filesystem paths from `application.yaml`, replace with S3 bucket/prefix config | Backend | `caseware.s3.lake-bucket`, `caseware.s3.events-bucket` properties |
| 2.7 | Create S3 VPC endpoint (gateway type) to avoid NAT costs for S3 traffic | Infra | VPC endpoint attached to route tables |

**Exit criteria:** App writes JSONL to S3, event files land in S3, no local filesystem dependency for lake/events.

---

## Phase 3 — Containerize & Deploy to ECS (Week 3–4)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 3.1 | Update `Dockerfile` for production: multi-stage build, non-root user, health check | Backend | Production-ready Dockerfile |
| 3.2 | Push images to ECR (CI pipeline or manual) | Backend | Images tagged `latest` + git SHA |
| 3.3 | Create ECS cluster (Fargate) | Infra | Cluster running |
| 3.4 | Create ECS task definition for ingestion service | Infra | Task def with CPU/memory limits, IAM task role, env vars for RDS + S3 |
| 3.5 | Create ECS scheduled task (EventBridge rule → ECS RunTask every 60s) | Infra | Scheduled ingestion runs automatically |
| 3.6 | Create ALB in public subnets, TLS certificate via ACM | Infra | ALB with HTTPS listener |
| 3.7 | Create ECS service for search proxy (Fargate, auto-scaling 1–4 tasks) | Infra | Service behind ALB target group |
| 3.8 | Configure ALB routing: `/ingest` → ingestion task, `/search` → search proxy | Infra | Path-based routing rules |
| 3.9 | Add health check endpoint `/health` to Spring Boot (already exists) | Backend | ALB health check passes |
| 3.10 | Configure ECS auto-scaling policy (CPU > 70% → scale out) | Infra | Auto-scaling policy active |

**Exit criteria:** Ingestion runs on schedule, search proxy accessible via ALB HTTPS, auto-scaling configured.

---

## Phase 4 — OpenSearch (Week 4–5)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 4.1 | Provision OpenSearch domain: 3 data nodes (`r6g.large.search`), Multi-AZ, encrypted | Infra | OpenSearch endpoint in private subnet |
| 4.2 | Define index mappings for `cases` and `customers` | Backend | Index templates with field types, analyzers |
| 4.3 | Create OpenSearch indexing logic in ingestion service (`/_bulk` API) | Backend | After S3 write, bulk-index documents into OpenSearch |
| 4.4 | Implement upsert by `case_id` / `customer_id` for idempotency | Backend | `_bulk` with `index` action (upserts by doc ID) |
| 4.5 | Replace Python TF-IDF ranker with OpenSearch query proxy | Backend | `/search` endpoint queries OpenSearch instead of calling Python ranker |
| 4.6 | Configure OpenSearch access policy: allow only from ECS task role | Infra | Fine-grained access control or IP-based policy |
| 4.7 | Set up automated snapshots to S3 | Infra | Hourly snapshots, 14-day retention |

**Exit criteria:** Search queries return ranked results from OpenSearch, upserts are idempotent, snapshots configured.

---

## Phase 5 — Monitoring & Alerting (Week 5)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 5.1 | Configure CloudWatch log groups for ECS tasks (ingestion + search proxy) | Infra | Logs visible in CloudWatch |
| 5.2 | Create CloudWatch dashboard: ingestion lag, search latency, error rates, task count | Infra | Dashboard with key metrics |
| 5.3 | Create alarms | Infra | Alarms configured (see below) |
| 5.4 | Set up SNS topic + email/Slack notification for alarms | Infra | Alert notifications working |
| 5.5 | Add structured logging (JSON) to Spring Boot for CloudWatch Insights queries | Backend | Log format updated |

### Alarms

| Alarm | Condition | Action |
|---|---|---|
| Ingestion failure | ECS task exit code ≠ 0, 3 consecutive | SNS alert |
| Ingestion lag | No checkpoint update in 10 min | SNS alert |
| Search latency | ALB target response time p95 > 500ms | SNS alert |
| Search errors | ALB 5xx count > 10 in 5 min | SNS alert |
| RDS CPU | > 80% for 5 min | SNS alert |
| OpenSearch cluster health | Yellow or Red for 5 min | SNS alert |
| S3 error rate | 4xx/5xx on `caseware-lake` > 1% | SNS alert |

**Exit criteria:** Dashboard live, alarms trigger on simulated failures, notifications received.

---

## Phase 6 — Security Hardening (Week 5–6)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 6.1 | Store RDS credentials in Secrets Manager, inject via ECS task definition | Infra | No hardcoded credentials |
| 6.2 | Restrict security groups: ECS → RDS (5432), ECS → OpenSearch (443), ALB → ECS (8080) | Infra | Minimal SG rules |
| 6.3 | Enable S3 bucket policies: deny unencrypted uploads, deny public access | Infra | Bucket policies enforced |
| 6.4 | Enable RDS IAM authentication (optional, alternative to password) | Infra | IAM auth configured |
| 6.5 | Review IAM task role: ensure least privilege (only S3/RDS/OpenSearch actions needed) | Infra | IAM policy reviewed and trimmed |
| 6.6 | Enable VPC Flow Logs for audit | Infra | Flow logs to CloudWatch |
| 6.7 | Run security scan (AWS Trusted Advisor or third-party) | Infra | No critical findings |

**Exit criteria:** No public access to data stores, credentials rotated via Secrets Manager, least-privilege IAM verified.

---

## Phase 7 — Validation & Load Testing (Week 6–7)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 7.1 | Seed RDS with representative dataset (10M cases, 500K customers) | QA | Test data loaded |
| 7.2 | Run full ingestion cycle, verify S3 lake partitions | QA | JSONL files in `s3://caseware-lake/cases/yyyy-MM-dd/data.jsonl` |
| 7.3 | Verify idempotency: run same ingestion twice, confirm no duplicates in S3 or OpenSearch | QA | Identical output on second run |
| 7.4 | Verify replay: reset checkpoint to past date, confirm correct re-ingestion | QA | Lake overwrites, OpenSearch upserts, no duplicates |
| 7.5 | Load test search: 1,000 QPS sustained for 10 min | QA | p95 < 300ms, no 5xx errors |
| 7.6 | Failure test: kill ECS task mid-ingestion, verify recovery on next run | QA | Next scheduled run picks up from checkpoint, no data corruption |
| 7.7 | Failure test: RDS failover, verify ingestion resumes | QA | Ingestion reconnects after failover (~30s) |
| 7.8 | Verify CloudWatch alarms fire during failure tests | QA | Alarms triggered, notifications received |

**Exit criteria:** All NFR targets met under load, failure scenarios handled gracefully, alarms validated.

---

## Phase 8 — Go-Live & Cutover (Week 7–8)

| # | Task | Owner | Deliverable |
|---|---|---|---|
| 8.1 | Final data migration: snapshot source DB → restore to RDS | Backend + Infra | Production data in RDS |
| 8.2 | Run initial full ingestion to populate S3 lake and OpenSearch index | Backend | Lake and search index populated |
| 8.3 | Switch DNS / API endpoint to ALB | Infra | Production traffic routed to AWS |
| 8.4 | Monitor first 24h: dashboards, alarms, log review | All | No critical issues |
| 8.5 | Decommission Docker Compose environment | Infra | Local environment shut down |
| 8.6 | Document runbooks: restart ingestion, reset checkpoint, re-index OpenSearch | Backend | Runbooks in wiki/repo |

**Exit criteria:** Production traffic flowing through AWS, Docker Compose decommissioned, runbooks documented.

---

## Summary Timeline

```
Week 1    ██ Phase 0 — Foundation (VPC, IAM, ECR, KMS)
Week 2    ██ Phase 1 — Database (RDS + checkpoint migration)
Week 2-3  ██ Phase 2 — S3 Lake & Events
Week 3-4  ██ Phase 3 — ECS Fargate + ALB
Week 4-5  ██ Phase 4 — OpenSearch
Week 5    ██ Phase 5 — Monitoring & Alerting
Week 5-6  ██ Phase 6 — Security Hardening
Week 6-7  ██ Phase 7 — Validation & Load Testing
Week 7-8  ██ Phase 8 — Go-Live & Cutover
```

**Total: ~8 weeks** (2 people: 1 backend, 1 infra)

---

## Risk Register

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| OpenSearch sizing insufficient for 250M docs | Search latency exceeds NFR | Medium | Load test in Phase 7 with production-scale data. Right-size nodes before go-live. |
| RDS read replica lag during peak ingestion | Stale checkpoint reads, duplicate processing | Low | Checkpoint reads/writes go to primary. Only delta queries use read replica. |
| S3 eventual consistency on overwrites | Reader sees stale JSONL partition | Low | S3 provides strong read-after-write consistency since 2020. Not an issue. |
| ECS scheduled task overlap (previous run still running) | Double ingestion | Medium | Set ECS task concurrency limit to 1. Checkpoint conditional update prevents duplicates. |
| Cost overrun on OpenSearch | Monthly bill exceeds estimate | Medium | Start with smallest viable cluster. Use reserved instances after validating sizing. |
| Team unfamiliar with Terraform/CDK | IaC delays | Low | Use AWS CDK if team knows TypeScript/Java; or Terraform if experienced. Choose one. |

---

## Assumptions

1. Source PostgreSQL database schema (customers, cases) does not change during migration.
2. Current ingestion volume (5,000 updates/sec peak) is the upper bound for the first 6 months.
3. JSONL format is retained for the lake (no Parquet conversion in Option A).
4. Single AWS region deployment (Multi-AZ within one region). Multi-region is a future consideration.
5. The Python TF-IDF ranker is fully replaced by OpenSearch — no hybrid search phase.
6. Team has 2 engineers available: 1 backend (Java/Spring), 1 infrastructure (Terraform/CDK).
