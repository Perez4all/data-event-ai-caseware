import json
import os
import textwrap
from pathlib import Path

import pytest
from fastapi.testclient import TestClient


# ── helpers ──────────────────────────────────────────────────────────

SAMPLE_CASES = [
    {"caseId": 1, "customerId": 1, "title": "Billing discrepancy on invoice #1001",
     "description": "Customer reports overcharge on monthly billing statement.", "status": "open"},
    {"caseId": 2, "customerId": 1, "title": "Audit log access request",
     "description": "Customer requests full audit trail export for Q4.", "status": "open"},
    {"caseId": 3, "customerId": 2, "title": "Compliance review needed for GDPR",
     "description": "Annual compliance check required for EU data residency.", "status": "in_progress"},
    {"caseId": 4, "customerId": 2, "title": "Security vulnerability report",
     "description": "Customer flagged a potential XSS security vulnerability on the portal.", "status": "open"},
    {"caseId": 5, "customerId": 3, "title": "Onboarding new team members",
     "description": "Request to onboard 15 new users to the platform.", "status": "in_progress"},
    {"caseId": 6, "customerId": 3, "title": "Refund request for duplicate charge",
     "description": "Duplicate billing detected; refund of $250 requested.", "status": "open"},
    {"caseId": 7, "customerId": 4, "title": "Escalation: SLA breach on ticket #887",
     "description": "Response SLA was breached; customer demands escalation.", "status": "open"},
    {"caseId": 8, "customerId": 4, "title": "Data migration from legacy system",
     "description": "Migrate 50k records from legacy CRM to new platform.", "status": "in_progress"},
    {"caseId": 9, "customerId": 5, "title": "Tax calculation error",
     "description": "Sales tax computed incorrectly for NY-based transactions.", "status": "open"},
    {"caseId": 10, "customerId": 5, "title": "Fraud detection alert",
     "description": "Suspicious login attempts detected from unknown IPs.", "status": "open"},
]


def _write_lake(tmp_path: Path, cases: list[dict], partition: str = "2026-03-20") -> Path:
    """Write cases as JSONL to a partition under a tmp lake directory."""
    lake = tmp_path / "lake" / "cases"
    partition_dir = lake / partition
    partition_dir.mkdir(parents=True, exist_ok=True)
    jsonl = "\n".join(json.dumps(c) for c in cases) + "\n"
    (partition_dir / "data.jsonl").write_text(jsonl, encoding="utf-8")
    return lake


@pytest.fixture()
def client(tmp_path, monkeypatch):
    """Create a TestClient with a populated lake directory."""
    lake = _write_lake(tmp_path, SAMPLE_CASES)
    monkeypatch.setenv("LAKE_CASES_PATH", str(lake))
    # Re-import so the module picks up the patched env var
    import importlib
    import main as main_module
    monkeypatch.setattr(main_module, "LAKE_PATH", lake)
    main_module.build_index()
    with TestClient(main_module.app) as c:
        yield c


@pytest.fixture()
def empty_client(tmp_path, monkeypatch):
    """Create a TestClient with an empty lake directory (no cases)."""
    lake = tmp_path / "lake" / "cases"
    lake.mkdir(parents=True, exist_ok=True)
    import main as main_module
    monkeypatch.setattr(main_module, "LAKE_PATH", lake)
    main_module.build_index()
    with TestClient(main_module.app) as c:
        yield c


# ── /health ──────────────────────────────────────────────────────────

class TestHealth:

    def test_health_returns_ok(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "ok"

    def test_health_reports_indexed_count(self, client):
        resp = client.get("/health")
        assert resp.json()["indexed"] == len(SAMPLE_CASES)

    def test_health_empty_index(self, empty_client):
        resp = empty_client.get("/health")
        assert resp.json()["indexed"] == 0


# ── /search ──────────────────────────────────────────────────────────

class TestSearch:

    def test_search_returns_results(self, client):
        resp = client.post("/search", json={"query": "billing invoice"})
        assert resp.status_code == 200
        results = resp.json()["results"]
        assert len(results) > 0

    def test_search_top_result_is_relevant(self, client):
        resp = client.post("/search", json={"query": "billing invoice overcharge"})
        results = resp.json()["results"]
        # The billing case should rank first
        assert results[0]["case_id"] == 1

    def test_search_respects_top_k(self, client):
        resp = client.post("/search", json={"query": "billing", "top_k": 2})
        results = resp.json()["results"]
        assert len(results) <= 2

    def test_search_returns_score(self, client):
        resp = client.post("/search", json={"query": "audit compliance"})
        results = resp.json()["results"]
        for r in results:
            assert "score" in r
            assert isinstance(r["score"], float)
            assert r["score"] > 0

    def test_search_scores_are_descending(self, client):
        resp = client.post("/search", json={"query": "security vulnerability"})
        results = resp.json()["results"]
        scores = [r["score"] for r in results]
        assert scores == sorted(scores, reverse=True)

    def test_search_result_has_expected_fields(self, client):
        resp = client.post("/search", json={"query": "refund"})
        results = resp.json()["results"]
        assert len(results) > 0
        first = results[0]
        assert "case_id" in first
        assert "title" in first
        assert "status" in first
        assert "score" in first

    def test_search_no_match_returns_empty(self, client):
        resp = client.post("/search", json={"query": "xyznonexistent12345"})
        results = resp.json()["results"]
        assert results == []

    def test_search_empty_index_returns_503(self, empty_client):
        resp = empty_client.post("/search", json={"query": "billing"})
        assert resp.status_code == 503

    def test_search_missing_query_returns_422(self, client):
        resp = client.post("/search", json={})
        assert resp.status_code == 422


# ── /refresh ─────────────────────────────────────────────────────────

class TestRefresh:

    def test_refresh_returns_indexed_count(self, client):
        resp = client.post("/refresh")
        assert resp.status_code == 200
        assert resp.json()["indexed"] == len(SAMPLE_CASES)

    def test_refresh_picks_up_new_data(self, client, tmp_path, monkeypatch):
        import main as main_module
        lake = main_module.LAKE_PATH
        # Add a new partition with an extra case
        new_case = {"caseId": 99, "customerId": 10, "title": "New case", "description": "Brand new.", "status": "open"}
        new_partition = lake / "2026-03-21"
        new_partition.mkdir(parents=True, exist_ok=True)
        (new_partition / "data.jsonl").write_text(json.dumps(new_case) + "\n", encoding="utf-8")

        resp = client.post("/refresh")
        assert resp.json()["indexed"] == len(SAMPLE_CASES) + 1

    def test_refresh_deduplicates_by_case_id(self, client, tmp_path):
        import main as main_module
        lake = main_module.LAKE_PATH
        # Write a duplicate caseId=1 in a newer partition
        dup = {"caseId": 1, "customerId": 1, "title": "Updated billing case",
               "description": "Updated description.", "status": "closed"}
        new_partition = lake / "2026-03-21"
        new_partition.mkdir(parents=True, exist_ok=True)
        (new_partition / "data.jsonl").write_text(json.dumps(dup) + "\n", encoding="utf-8")

        client.post("/refresh")
        resp = client.post("/search", json={"query": "Updated billing case"})
        results = resp.json()["results"]
        # Should find the updated version, not a duplicate
        matching = [r for r in results if r["case_id"] == 1]
        assert len(matching) <= 1
        # Total count should remain the same (dedup)
        health = client.get("/health")
        assert health.json()["indexed"] == len(SAMPLE_CASES)

