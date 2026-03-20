import os
import json
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

LAKE_PATH = Path(os.getenv("LAKE_CASES_PATH", "./lake/cases"))

_cases: list[dict] = []
_vectorizer: TfidfVectorizer | None = None
_tfidf_matrix = None


def build_index() -> None:
    global _cases, _vectorizer, _tfidf_matrix
    cases_by_id: dict[str, dict] = {}
    # Sort ascending so newer partitions (yyyy-MM-dd) overwrite stale ones
    for partition in sorted(LAKE_PATH.glob("*/data.jsonl")):
        for line in partition.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line:
                case = json.loads(line)
                cases_by_id[case["caseId"]] = case
    _cases = list(cases_by_id.values())
    if _cases:
        texts = [f"{c.get('title', '')} {c.get('description', '')}" for c in _cases]
        _vectorizer = TfidfVectorizer()
        _tfidf_matrix = _vectorizer.fit_transform(texts)
    else:
        _vectorizer = None
        _tfidf_matrix = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    build_index()
    yield


app = FastAPI(title="Case Search Ranker", lifespan=lifespan)


class SearchQuery(BaseModel):
    query: str
    top_k: int = 10


@app.post("/search")
def search(search_query: SearchQuery):
    if not _cases or _vectorizer is None or _tfidf_matrix is None:
        raise HTTPException(status_code=503, detail="Index not ready")
    query_vec = _vectorizer.transform([search_query.query])
    scores = cosine_similarity(query_vec, _tfidf_matrix).flatten()
    top_indices = scores.argsort()[::-1][: search_query.top_k]
    return {
        "results": [
            {
                "case_id": _cases[i]["caseId"],
                "title": _cases[i].get("title"),
                "status": _cases[i].get("status"),
                "score": round(float(scores[i]), 4),
            }
            for i in top_indices
            if scores[i] > 0
        ]
    }


@app.post("/refresh")
def refresh():
    build_index()
    return {"indexed": len(_cases)}


@app.get("/health")
def health():
    return {"status": "ok", "indexed": len(_cases)}