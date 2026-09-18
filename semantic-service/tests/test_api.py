import os

os.environ.setdefault("JINJIN_SEMANTIC_EMBEDDING_BACKEND", "none")

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health() -> None:
    response = client.get("/api/v1/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["embedding_dim"] == 384


def test_analyze_poem() -> None:
    response = client.post(
        "/api/v1/analyze",
        json={"text": "今天语文课老师抽查登鹳雀楼，我第一句背错了。"},
    )
    assert response.status_code == 200
    body = response.json()
    assert "学习" in [h["label"] for h in body["layer_hints"] if h["layer"] == "SCHEMA"]
    assert body["embedding_dim"] == 384
    assert len(body["embedding"]) == 384


def test_understand_query() -> None:
    response = client.post(
        "/api/v1/understand",
        json={"query": "老师问我登鹳雀楼的内容"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["intent"] == "RECALL_CONTENT"
    assert any(h["label"] == "古诗" for h in body["theme_hints"])


def test_embed_batch() -> None:
    response = client.post(
        "/api/v1/embed",
        json={"texts": ["张三担心付款周期", "登鹳雀楼全文"]},
    )
    assert response.status_code == 200
    body = response.json()
    assert len(body["items"]) == 2
