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
