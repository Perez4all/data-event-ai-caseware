from fastapi import FastAPI

app = FastAPI(title = "Case Search Ranker")

@app.get("/health")
def health():
    return {"status": "ok"}