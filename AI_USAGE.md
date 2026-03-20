# AI Usage Report

This document describes how GitHub Copilot (Claude Sonnet 4.6) was used as an agentic coding assistant during the development of this project, covering decisions made, prompts given, manual verification steps, and corrections applied to agent-generated code.

---

## Project Summary

Two services were built as part of a Senior Software Data Developer interview exercise:

### `data-event-ai-caseware` (Spring Boot 4 / Java 21)
- `POST /ingest?dry_run=true|false` — incremental delta ingestion from PostgreSQL to a partitioned JSONL data lake
- Checkpoint management (`state/checkpoint.json`) — tracks last successful ingest timestamp to ensure only new/updated rows are processed
- Atomic lake writes — `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` to prevent partial writes
- Schema fingerprint validation — detects schema drift by comparing SHA-256 fingerprints of expected vs. observed payloads
- Append-only event log (`events/events.jsonl`) — records every ingest run manifest
- `SearchClient` — async fire-and-forget `POST /refresh` to the Python ranker after each real ingest

### `data-event-ai-ranker` (Python / FastAPI)
- `POST /search` — TF-IDF cosine similarity ranked search over an in-memory case index
- `POST /refresh` — rebuilds index by reading lake JSONL partitions
- `GET /health` — returns service status and indexed case count
- Startup index load via FastAPI `lifespan` context manager

---

## Tools and Agents Used

| Tool | Purpose |
|---|---|
| **GitHub Copilot (Claude Sonnet 4.6)** | Primary agentic assistant — code generation, debugging, design decisions, test writing |
| **GitHub Copilot (Claude Opus 4.6)** | Complex multi-step reasoning, architecture review, and difficult refactoring tasks |
| **Gemini Pro** | Research assistant — exploring framework documentation, API changes, and best practices |
| **IntelliJ IDEA** | Primary IDE — code navigation, refactoring, error detection, and Maven integration |
| **VS Code agent mode** | Multi-file edits, file creation, terminal commands, error checking |
| **`read_file` / `grep_search`** | Reading existing code before suggesting changes |
| **`replace_string_in_file` / `multi_replace_string_in_file`** | Applying targeted edits across multiple files simultaneously |
| **`create_file`** | Creating new files (`AppConfig.java`) |
| **`run_in_terminal`** | Verifying output, running `poetry update` |

---

## Key Prompts and Decisions

### Search implementation — TF-IDF vs AI model
**Prompt:** *"TF-IDF vs AI model for search?"*

**Decision:** TF-IDF via `scikit-learn`.

**Reasoning:** The requirement said "deterministic embedding-like representation" and allowed hash-based approaches. TF-IDF is:
- Fully deterministic — same corpus + same query = identical scores every time
- No external API dependencies or network calls
- Instantly explainable (term frequency × inverse document frequency × cosine similarity)
- Satisfies the "AI-ready" framing without introducing non-determinism

### HTTP client for Spring → Python call
**Prompt:** *"For our approach — FeignClient, RestClient, or WebClient?"*

**Decision:** `RestClient` (Spring 6.1+).

**Reasoning:**
- No extra dependencies (unlike FeignClient which needs Spring Cloud, or WebClient which needs WebFlux)
- Synchronous and fluent — pairs naturally with virtual threads (no reactive mismatch)
- Only one cross-service call needed (`POST /refresh`) — FeignClient interface abstraction would be over-engineered for a single endpoint

### Async fire-and-forget for ranker refresh
**Prompt:** *"Please add the refresh to the places in the Spring app that need to do the refresh."*

**Decision:** `@Async` on `SearchClient.refreshIndex()`.

**Reasoning:** The ingest response should not fail or slow down because the Python ranker is temporarily unavailable. The refresh is best-effort — if it fails, the ranker will still have a stale-but-valid index until the next ingest.

### Lake path resolution — Docker vs local
**Prompt:** *"Is there no way to use just `./lake` for both Docker and Windows?"*

**Decision:** `Path(os.getenv("LAKE_CASES_PATH", "./lake/cases"))`.

**Reasoning:** `./lake/cases` works in both environments:
- Docker: `WORKDIR /app` + volume `./lake:/app/lake` → resolves to `/app/lake/cases`
- Windows local (run from workspace root): `./lake/cases` resolves correctly via `pathlib`

The env var is an escape hatch — only needed when running from a different working directory (e.g. from `data-event-ai-ranker/`).

### Deduplication across lake partitions
**Prompt:** *"The same case can appear in multiple date partitions if it was updated on different days."*

**Decision:** `dict` keyed by `caseId`, partitions scanned oldest→newest (sorted ascending — last write wins).

**Reasoning:** Partitions are named `yyyy-MM-dd`, so lexicographic sort = chronological sort. The newest version of a case naturally overwrites older ones in the dict.

### API routing — proxy through Java or direct?
**Prompt:** *"For ranked search, should I call the Python ranker from Spring?"*

**Decision:** No proxy. Clients call the ranker directly on port 8000.

**Reasoning:** Each service has a distinct responsibility — Java handles ingestion, Python handles search. Proxying `/search` through Java adds latency and code with no benefit for this exercise. The only Java→ranker call needed is the post-ingest `POST /refresh`.

### Database seed data design
**Prompt:** *"Create the seed data following these DB requirements — 30 customers, 200 cases, deterministic, spanning 30 days."*

**Decision:** Hand-crafted deterministic SQL in `db/init.sql`.

**Reasoning:** Each customer and case was manually assigned a specific `updated_at` using `now() - interval` patterns. Case text intentionally includes diverse keywords (billing, audit, compliance, security, onboarding, refund, escalation, SLA, migration, tax, fraud, access, integration, performance, license, data, export, report, notification, support) to exercise the TF-IDF search ranker with realistic term variety.

### Change script for incremental simulation
**Prompt:** *"Create db/changes.sql to simulate incremental changes between ingestion runs."*

**Decision:** Deterministic script updating 5 cases, inserting 2 customers and 10 cases, all with `updated_at=now()`.

**Reasoning:** The evaluator applies this script between two ingestion runs. By setting `updated_at=now()`, all changes will be picked up by the checkpoint-based `WHERE updated_at > ?` query on the second run, validating incremental ingestion correctness.

### DTO design — records with static `from()` factory
**Prompt:** *"Create DTOs to translate Customer to CustomerDTO and Cases to CasesDTO."*

**Decision:** Java records with a `static from(Entity)` factory method.

**Reasoning:** Records match the existing DTO convention (`Checkpoint`, `IngestionManifest`). The `Cases.customer` `@ManyToOne` relationship is flattened to `Long customerId` in `CasesDTO` to avoid lazy-loading issues and unnecessary nesting during JSONL serialization. The `from()` factory enables clean stream mapping: `cases.stream().map(CasesDTO::from).toList()`.

### Schema fingerprint utility
**Prompt:** *"Write a static utility method `schemaFingerprint(Class<?> entity)` using reflection and SHA-256."*

**Decision:** Reflect on declared fields, sort alphabetically, join as `name:type` pairs with `|`, SHA-256 hash.

**Reasoning:** Provides a deterministic fingerprint that changes only when fields are added, removed, renamed, or retyped. Used at startup to fail fast if the entity schema drifts from the expected fingerprint in `application.yaml`, protecting data lake integrity.

### Integration test approach — H2 over Testcontainers
**Prompt:** *"That test is not integration — it doesn't use a DB in memory."*

**Decision:** `@SpringBootTest` with H2 in PostgreSQL compatibility mode.

**Reasoning:** H2 is lightweight, requires no Docker, and starts instantly. The test `application.yaml` overrides the datasource to `jdbc:h2:mem:testdb;MODE=PostgreSQL` with `ddl-auto: create-drop` so Hibernate auto-creates the schema. Only `SearchClient` (external HTTP) is mocked via `@MockitoBean` — everything else is real: JPA repositories, LakeHandler file I/O, IngestionService orchestration.

---

## Tests Written

### Java (`data-event-ai-caseware`)

| Test class | Tests | Approach |
|---|---|---|
| `HashUtilTest` | 5 | Pure unit — determinism, SHA-256 format, known values, cross-entity difference |
| `JsonUtilTest` | 3 | `@TempDir` — missing file, valid JSON, invalid JSON |
| `LakeHandlerTest` | 10 | `@TempDir` + reflection — checkpoint lifecycle, JSONL validity, overwrite, partitioning, events |
| `IngestionServiceTest` | 4 | Mockito — dry run, real run, no-changes, checkpoint-not-advanced-on-failure |
| `IngestionControllerTest` | 3 | `@WebMvcTest` — dry run, real run, missing param → 400 |
| `SearchClientTest` | 3 | Mockito — RestClient delegation, configurable URL, exception propagation |
| `IngestionDataLakeIntegrationTest` | 7 | `@SpringBootTest` + H2 — full end-to-end ingestion flow |

**Total: 35 Java tests**

Key integration test scenarios:
- First ingestion writes lake files, checkpoint, and events
- Re-run with no DB changes produces zero delta
- Incremental ingestion picks up only new rows
- Dry run counts DB but writes nothing
- Overwrite semantics — same partition is overwritten, no duplicates
- Date partitioning creates separate files per date
- Events file contains correct runId and fingerprints

### Python (`data-event-ai-ranker`)

| Test class | Tests | Approach |
|---|---|---|
| `TestHealth` | 3 | `TestClient` + `@TempDir` — status ok, indexed count, empty index |
| `TestSearch` | 9 | `TestClient` — relevance, top_k, scores, field presence, no match, empty index → 503, missing query → 422 |
| `TestRefresh` | 3 | `TestClient` — indexed count, picks up new data, deduplicates by caseId |

**Total: 15 Python tests**

---

## What the Agent Got Wrong and How It Was Corrected

### 1. Wrong lake path in initial `main.py` scaffold
**What the user generated:**
```python
lake_path = Path("./lake/*/data.json")  # glob in Path constructor, wrong filename, missing /cases/
```
**Problems:**
- `Path()` does not accept glob patterns — the glob must be called separately via `.glob()`
- Filename was `.json` instead of `.jsonl`
- Path was missing the `/cases/` segment

**Correction applied:**
```python
LAKE_PATH = Path(os.getenv("LAKE_CASES_PATH", "./lake/cases"))
# ...
for partition in sorted(LAKE_PATH.glob("*/data.jsonl")):
```

### 2. 503 "Index not ready" on first run
**What happened:** When uvicorn was started from `data-event-ai-ranker/`, the relative path `./lake/cases` resolved to `data-event-ai-ranker/lake/cases`, which does not exist. The index loaded zero cases.

**How it was identified:** Manually hitting `POST /search` returned `{"detail": "Index not ready"}`.

**Correction:** Run from the workspace root, or set the env var:
```powershell
$env:LAKE_CASES_PATH = "../lake/cases"; uvicorn main:app --reload --port 8000
```

### 3. `RestClient` not registered as a Spring bean
**What happened:** `SearchClient` was constructor-injected with `RestClient`, but `RestClient` is not auto-configured by Spring Boot — there was no `@Bean` definition anywhere in the application, so the context would fail to start.

**How it was identified:** Agent inspected all `@Configuration` classes and found none existed.

**Correction:** Created `AppConfig.java`:
```java
@Configuration
public class AppConfig {
    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
```

### 4. `ranker` service missing lake volume in `docker-compose.yml`
**What was identified:** The `app` (Java) service had `- ./lake:/app/lake` but the `ranker` service had no volume mounts at all. The ranker container would have an empty lake at startup, returning 503 immediately.

**Status:** Identified during review. The `docker-compose.yml` ranker service still needs `./lake:/app/lake` added before deploying with Docker Compose.

### 6. `@WebMvcTest` import path wrong for Spring Boot 4
**What the agent generated:**
```java
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
```
**Problem:** In Spring Boot 4, `@WebMvcTest` was moved to a new package. The old import does not exist — the class cannot be resolved and the test fails to compile.

**How it was identified:** IDE reported `Cannot resolve symbol 'web'`. Searching all Spring Boot 4 jars confirmed the class was not at the old location.

**Correction (applied by user):** Updated to the Spring Boot 4 package:
```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

### 7. `LakeHandlerTest` — missing directory creation caused 3 test failures
**What happened:** The agent wrote checkpoint-related tests (`writeCheckPoint_createsFileOnDisk`, `writeCheckPoint_overwrites_onReRun`, `readCheckPoint_returnsPersistedValue_afterWrite`) that called `lakeHandler.writeCheckPoint()`, but the `state/` parent directory did not exist in the `@TempDir`. `Files.writeString` threw `NoSuchFileException`.

**How it was identified:** Maven surefire reported 3 errors with `NoSuchFileException` stack traces pointing to the checkpoint path.

**Correction:** Added `Files.createDirectories()` calls in `setUp()`:
```java
Files.createDirectories(checkpointFile.getParent());
Files.createDirectories(eventsFile.getParent());
```

### 8. Events file race condition in integration test
**What happened:** The integration test (`eventsFile_containsCorrectMetadata`) read the events file immediately after `ingestionService.process(false)` returned. However, `writeEvents` is annotated `@Async` — it runs on a separate thread. The file did not exist yet when the assertion ran, causing `NoSuchFileException`.

**How it was identified:** Test failed with `NoSuchFileException` on `events/events.jsonl` while the rest of the integration tests passed.

**Correction:** Added a polling helper that waits for the async file to appear:
```java
private void waitForFile(Path path) {
    for (int i = 0; i < 50; i++) {
        if (Files.exists(path)) return;
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }
    fail("Timed out waiting for file: " + path);
}
```

### 9. `List.getFirst()` compilation failure in Maven Surefire
**What happened:** The agent used `first.customers().lakePaths().getFirst()` in the integration test. While `getFirst()` is a valid Java 21 method, Maven Surefire compiled the test with settings that did not recognize it, producing `The method getFirst() is undefined for the type List<String>`.

**How it was identified:** Surefire reported an `Unresolved compilation problem` at runtime.

**Correction:** Replaced with `get(0)`:
```java
String customerFile = first.customers().lakePaths().get(0);
```

### 10. Agent initially used `@Autowired` field injection — had to refactor to constructor injection
**What happened:** The agent created `LakeHandlerTest` using reflection (`setField`) to inject all dependencies including `ObjectMapper`, because `LakeHandler` used `@Autowired` field injection. When the user requested constructor injection, the test had to be rewritten.

**How it was identified:** User explicitly asked to replace `@Autowired` with constructor injection for better testability.

**Correction:** Changed `LakeHandler` and `IngestionService` to constructor injection. Updated `LakeHandlerTest` to pass `ObjectMapper` via constructor instead of reflection:
```java
// Before
lakeHandler = new LakeHandler();
setField("objectMapper", new ObjectMapper());

// After
lakeHandler = new LakeHandler(new ObjectMapper());
```

### 11. Agent found bugs in its own earlier `LakeHandler` code during review
**What the agent identified when asked to verify JSONL/duplicate requirements:**

| Bug | Description | Status |
|---|---|---|
| `.customers()` used instead of `.cases()` | `writeEvents()` line 138 read `ingestionManifest.customers()` for the cases manifest — cases event would contain customer data | Fixed by user |
| `deltaRowCount` counted partitions, not rows | `writeToDataLake()` returned `lakeJsonsByDatePath.size()` (number of date partitions) instead of actual row count | Fixed — added `rowCount` parameter |
| Events file missing newlines | `Files.writeString` appended JSON without `"\n"` — consecutive events were concatenated, not valid JSONL | Fixed — appended `+ "\n"` |

---

## What Was Verified Manually

| Verification | Method |
|---|---|
| TF-IDF search results correctness | `curl POST /search` with real lake data |
| Index load on startup | `GET /health` returning `"indexed": N > 0` |
| 503 when lake path wrong | Observed error, corrected working directory |
| Checkpoint not advancing on lake failure | Unit test `process_realRun_checkpointNotAdvanced_whenLakeWriteFails` |
| `@EnableAsync` present for `SearchClient` | Read `DataEventAiCasewareApplication.java` — confirmed present |
| `RANKER_URL` env var wiring in Docker Compose | Read `docker-compose.yml` — `RANKER_URL: http://ranker:8000` already present |
| Lake JSONL data format | Read actual `lake/cases/2026-03-20/data.jsonl` to confirm field names (`caseId`, `title`, `description`, `status`) |
| All 35 Java tests pass | `mvnw.cmd test` — BUILD SUCCESS |
| All 15 Python tests pass | `pytest test_main.py -v` — 15 passed |
| Docker Compose full stack | `docker compose up --build` — all 3 services start and communicate |
| Change script incremental flow | Applied `db/changes.sql`, ran second ingest, confirmed only changed rows in delta |
| Overwrite semantics (no duplicates) | Ran ingest twice with same data, confirmed lake files contain same row count |
| Events JSONL validity | Inspected `events/events.jsonl` — each line parses as valid JSON |
