# Review Checklist

## 1. Ingestion Service (Spring Boot)

- [ ] `POST /ingest` endpoint accepts `dry_run` parameter
- [ ] Checkpoint-based incremental ingestion (`WHERE updated_at > checkpoint`)
- [ ] JSONL lake output partitioned by `yyyy-MM-dd`
- [ ] Atomic writes (`.tmp` + `ATOMIC_MOVE`) prevent half-written files
- [ ] Idempotent: re-running produces same result (overwrite semantics)
- [ ] Schema fingerprint validation on startup (`@PostConstruct`)
- [ ] Application fails fast if schema fingerprint mismatch
- [ ] Events appended to `events.jsonl` as audit trail
- [ ] Async event writing (`@Async`)
- [ ] `IngestionManifest` returned with run metadata

## 2. Search Ranker (Python FastAPI)

- [ ] `POST /search` returns TF-IDF ranked results
- [ ] `POST /refresh` reloads lake index
- [ ] `GET /health` health check endpoint
- [ ] Lifespan startup loads initial index from lake
- [ ] Handles empty lake gracefully (no crash on fresh start)

## 3. Data Integrity

- [ ] `HashUtil.schemaFingerprint()` produces stable hash per model class
- [ ] Fingerprint checked against config on every startup
- [ ] Lake files are immutable per date partition (overwrite = full replace)
- [ ] No partial writes possible (atomic move)
- [ ] Checkpoint updated only after successful lake write

## 4. Docker & Deployment

- [ ] `docker-compose.yml` orchestrates postgres, app, ranker
- [ ] Named volumes for `lake`, `state`, `events` (not bind mounts)
- [ ] Data reset via `docker compose down -v`
- [ ] `.dockerignore` in ranker excludes `__pycache__/`, `venv/`, `.pyc`
- [ ] Containers start in correct order (depends_on with health checks)
- [ ] Environment variables configured for all services
- [ ] Dockerfiles use multi-stage or minimal base images

## 5. Database

- [ ] `db/init.sql` creates schema (customers, cases tables)
- [ ] `db/changes.sql` contains incremental migrations
- [ ] Indexes on `updated_at` columns for checkpoint queries
- [ ] PostgreSQL version pinned in docker-compose

## 6. Error Handling & Resilience

- [ ] `LakeHandler` creates lake directories on startup (`@PostConstruct`)
- [ ] Path normalization (`.normalize()`) prevents `./` path issues
- [ ] `writeToDataLake` handles missing parent directories (`Files.createDirectories`)
- [ ] Events directory created if missing before write
- [ ] Application recovers gracefully after container restart
- [ ] No data corruption on mid-ingestion failure

## 7. Security

- [ ] No hardcoded credentials in source code
- [ ] Database credentials via environment variables
- [ ] No sensitive data in logs
- [ ] `.dockerignore` prevents leaking local files into images

## 8. Testing

- [ ] Unit tests pass (`mvn test`)
- [ ] `IngestionServiceTest` covers ingestion flow
- [ ] `LakeHandlerTest` covers lake write logic
- [ ] `HashUtilTest` covers schema fingerprinting
- [ ] `JsonUtilTest` covers JSON serialization
- [ ] `SearchClientTest` covers ranker client
- [ ] `IngestionControllerTest` covers REST endpoint
- [ ] `IngestionDataLakeIntegrationTest` covers end-to-end flow
- [ ] Python `test_main.py` covers ranker endpoints

## 9. Documentation

- [ ] `README.md` — project overview, setup, run instructions
- [ ] `AI_USAGE.md` — AI tool usage disclosure
- [ ] `ARCHITECTURE_AWS.md` — AWS production architecture (2 options)
- [ ] `ARCHITECTURE_AWS_DIAGRAMS.md` — Mermaid diagrams for AWS options
- [ ] `EXECUTION_PLAN.md` — general execution plan
- [ ] `EXECUTION_PLAN_OPTION_A.md` — phased AWS migration plan
- [ ] `PYTHON_SETUP.md` — Python ranker setup guide

## 10. Code Quality

- [ ] No compiler warnings
- [ ] No unused imports
- [ ] Consistent code style across Java and Python
- [ ] Lombok used appropriately (not over-applied)
- [ ] DTOs are immutable (records)
- [ ] No `System.out.println` — all logging via SLF4J

## 11. Architecture & Design

- [ ] Clear 1-page architecture description (diagram + choices + reasoning)
- [ ] How it meets the NFRs (reliability, scaling, security, cost drivers)
- [ ] Tradeoffs/assumptions, failure handling, replay/idempotency strategy
- [ ] AWS architecture documented with three options (Custom App vs Lake Formation vs Hardened + Kinesis)
- [ ] Mermaid diagrams render correctly (architecture + sequence per option)
- [ ] Execution plan for Option A with phased delivery
