from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.schemas.requests import AnalyzeRequest, EmbedRequest, UnderstandRequest
from app.schemas.responses import AnalyzeResponse, EmbedResponse, HealthResponse, UnderstandResponse, VectorItem
from app.services import embedder
from app.services.analyzer import analyze
from app.services.query_understander import understand

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    embedder.ensure_ready()
    return HealthResponse(
        status="ok",
        embedding_backend=embedder.backend_name(),
        embedding_model=embedder.model_name(),
        embedding_dim=embedder.embedding_dim(),
    )


@router.post("/analyze", response_model=AnalyzeResponse)
def analyze_text(request: AnalyzeRequest) -> AnalyzeResponse:
    return analyze(request)


@router.post("/understand", response_model=UnderstandResponse)
def understand_query(request: UnderstandRequest) -> UnderstandResponse:
    return understand(request)


@router.post("/embed", response_model=EmbedResponse)
def embed_batch(request: EmbedRequest) -> EmbedResponse:
    if request.ids is not None and len(request.ids) != len(request.texts):
        raise HTTPException(status_code=400, detail="ids length must match texts length")

    vectors = embedder.embed_texts(request.texts)
    items: list[VectorItem] = []
    for i, text in enumerate(request.texts):
        item_id = request.ids[i] if request.ids else None
        items.append(VectorItem(id=item_id, text=text.strip(), embedding=vectors[i]))
    return EmbedResponse(
        items=items,
        embedding_model=embedder.model_name(),
        embedding_dim=embedder.embedding_dim(),
    )
