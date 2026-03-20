# Data Event AI Caseware — Stack & Architecture Decisions

## Overview

A data interop service that performs incremental ingestion from PostgreSQL, outputs JSONL to a local data lake, emits delta events, and exposes an AI-ready search over cases.

---

## Core Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17+ |
| Framework | Spring Boot (MVC) | 4.0.4 |
| Database | PostgreSQL | Latest |
| ORM | Spring Data JPA (Hibernate) | Managed by Spring Boot |
| Build | Maven | 3.9+ |
| Containerization | Docker + Docker Compose | — |
| Search Ranker | Python 3.11+ / FastAPI / scikit-learn | Sidecar module |

---

## Key Architectural Decisions

### 1. Spring MVC + Virtual Threads over WebFlux (Reactive)

**Decision:** Use traditional blocking MVC with Java virtual threads enabled.

**Why not Reactive (WebFlux)?**
- The app follows a **request → process → response** pattern. No endpoints require streaming or backpressure.
- `/ingest` is poll-based (checkpoint + `WHERE updated_at > ?`) — pull, not push.
- `/search` is a synchronous query → ranked results → response.
- JSONL file writes are inherently blocking I/O — reactive adds no value.
- R2DBC (reactive Postgres driver) is less mature than JDBC/JPA.
- Reactive code is harder to read, debug, and evaluate.

**Why Virtual Threads?**
- Spring Boot 4 supports Project Loom. One config line: `spring.threads.virtual.enabled=true`.
- Each request gets a lightweight virtual thread — no thread pool exhaustion.
- Handles production-scale NFRs (5,000 updates/sec, 1,000 QPS search bursts) with simple blocking code.
- Simpler code with the same concurrency benefits as reactive.

---

### 2. Spring Data JPA over JDBC Template

**Decision:** Use Spring Data JPA for database access.

**Reasoning:**
- Entity mapping simplifies working with `customers` and `cases` tables.
- Repository pattern keeps data access clean and testable.
- Incremental ingestion queries (`WHERE updated_at > ?`) are straightforward with JPA `@Query`.
- Hibernate manages connection pooling and transaction boundaries — important for the failure safety rule (checkpoint must not advance if writes fail).

---

### 3. PostgreSQL (Required by Spec)

**Decision:** Mandated by the project spec.

**Key points:**
- Schema DDL for `customers` and `cases` tables defined in `db/init.sql`.
- Required indexes specified by the contract.
- Change script in `db/changes.sql`.
- Checkpoint state stored in the database for durability.

---

### 4. Python TF-IDF Ranker (Sidecar)

**Decision:** A small Python module handles TF-IDF keyword extraction and relevance ranking for the AI-ready search.

**Why Python instead of Java?**
- `scikit-learn` provides TF-IDF vectorization + cosine similarity in ~15 lines. Java has no lightweight equivalent — Apache Lucene is overkill for this scope.
- Demonstrates cross-language integration capability.
- Stateless and lightweight — Spring Boot calls it, falls back to SQL if unavailable.

**Integration:**
- Spring Boot calls the Python ranker via subprocess (`ProcessBuilder`).
- No separate server, no network overhead, no Docker Compose complexity.
- Python code lives in `python-ranker/` directory.

---

### 5. Docker + Docker Compose

**Decision:** Containerize the full stack for reproducible local development.

**Services:**
- `postgres` — database with init scripts mounted
- `app` — Spring Boot service

---

## NFR Alignment (ARCHITECTURE_AWS.md)

The local app is not built for production scale, but the stack choices **connect directly** to the production NFRs:

| NFR | How the current stack addresses it |
|---|---|
| 5,000 updates/sec peak | Virtual threads — no thread pool bottleneck |
| 1,000 QPS search burst | Thread-per-request scales with Loom |
| p95 < 300ms search | No reactive overhead, direct JDBC queries |
| 99.9% availability | Simpler code = fewer bugs = higher reliability |
| 10 min freshness SLA | Poll-based incremental ingestion, configurable interval |
| PII / SOC2 / GDPR | Standard Spring Security + JPA audit logging |

Production scaling strategy (S3, SQS, ECS, OpenSearch, KMS) is documented separately in `ARCHITECTURE_AWS.md`.

---

## Prerequisites

- **Docker** and **Docker Compose** (v2+)
- **Java 21** (only if running tests outside Docker)
- **Maven 3.9+** (or use the included `mvnw` wrapper)
- **Python 3.13+** and **Poetry** (only if running ranker tests locally)

---

## How to Run

### Start everything (DB + App + Ranker)

```bash
docker compose up --build
```

This starts:
- **PostgreSQL 16** on port `5432` (seeded via `db/init.sql`)
- **Spring Boot app** on port `8080`
- **Python ranker** on port `8000`

### Start only the database

```bash
docker compose up postgres
```

### Apply the change script (between ingestion runs)

```bash
cat db/changes.sql | docker compose exec -T postgres psql -U interop -d interop
```

### Stop everything

```bash
docker compose down
```

---

## How to Run Tests

### Java tests (unit + integration with H2)

```bash
cd data-event-ai-caseware
./mvnw test
```

On Windows:

```cmd
cd data-event-ai-caseware
mvnw.cmd test
```

Tests include:
- `HashUtilTest` — schema fingerprint determinism and SHA-256 validation
- `JsonUtilTest` — JSON deserialization edge cases
- `LakeHandlerTest` — checkpoint, JSONL output, overwrite semantics
- `IngestionServiceTest` — dry run, real run, no-changes scenarios
- `IngestionControllerTest` — endpoint responses via MockMvc
- `SearchClientTest` — RestClient delegation
- `IngestionDataLakeIntegrationTest` — full `@SpringBootTest` with H2 in-memory DB

### Python ranker tests

```bash
cd data-event-ai-ranker
poetry install --no-root
poetry run pytest test_main.py -v
```

---

## Example API Calls

### Ingest (dry run) — count available rows without writing

```bash
curl -X POST "http://localhost:8080/ingest?dry_run=true"
```

Response:

```json
{
  "runId": "a1b2c3d4-...",
  "startedAt": "2026-03-20T10:00:00Z",
  "finishedAt": "2026-03-20T10:00:01Z",
  "customers": { "deltaRowCount": 30, "lakePaths": [], "schemaFingerprint": "3fda..." },
  "cases": { "deltaRowCount": 200, "lakePaths": [], "schemaFingerprint": "001a..." },
  "checkpointBefore": "1970-01-01T00:00:00Z",
  "checkpointAfter": "1970-01-01T00:00:00Z"
}
```

### Ingest (real run) — write JSONL to data lake and advance checkpoint

```bash
curl -X POST "http://localhost:8080/ingest?dry_run=false"
```

Response:

```json
{
  "runId": "e5f6g7h8-...",
  "startedAt": "2026-03-20T10:05:00Z",
  "finishedAt": "2026-03-20T10:05:02Z",
  "customers": { "deltaRowCount": 30, "lakePaths": ["./lake/customers/2026-03-20/data.jsonl"], "schemaFingerprint": "3fda..." },
  "cases": { "deltaRowCount": 200, "lakePaths": ["./lake/cases/2026-03-20/data.jsonl"], "schemaFingerprint": "001a..." },
  "checkpointBefore": "1970-01-01T00:00:00Z",
  "checkpointAfter": "2026-03-20T10:05:02Z"
}
```

### Search cases (via Python ranker)

```bash
curl -X POST "http://localhost:8000/search" \
  -H "Content-Type: application/json" \
  -d '{"query": "billing invoice overcharge", "top_k": 5}'
```

Response:

```json
{
  "results": [
    { "case_id": 1, "title": "Billing discrepancy on invoice #1001", "status": "open", "score": 0.8234 },
    { "case_id": 6, "title": "Refund request for duplicate charge", "status": "open", "score": 0.3102 }
  ]
}
```

### Health check (ranker)

```bash
curl http://localhost:8000/health
```

```json
{ "status": "ok", "indexed": 200 }
```

---

## Important Assumptions

- **Checkpoint is file-based** (`./state/checkpoint.json`). Deleting this file resets ingestion to epoch, causing a full re-ingest on the next run.
- **Schema fingerprint validation** runs at startup. If entity fields are changed without updating the fingerprint values in `application.yaml`, the app will fail fast with `FingerprintSchemaNotValidException`.
- **Lake files use overwrite semantics** per date partition. Re-running ingestion with the same data overwrites the file — no duplicates are created.
- **Events file uses append semantics** (`events/events.jsonl`). Each ingestion run appends 2 JSONL lines (one per table).
- **The ranker is best-effort**. If the Python sidecar is unavailable, ingestion still completes; only the `searchClient.refreshIndex()` call will fail silently (async).
- **`db/changes.sql`** is intended to be applied manually between two ingestion runs to simulate incremental database changes.
- **`ddl-auto: none`** in production config — Hibernate does not manage the schema. DDL is controlled by `db/init.sql`.
- **The `dry_run` parameter is required** on `/ingest`. Omitting it returns HTTP 400.

