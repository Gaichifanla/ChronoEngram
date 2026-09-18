from typing import List, Optional

from pydantic import BaseModel, Field


class LayerHint(BaseModel):
    layer: str = Field(description="SCHEMA | THEME | CONCEPT")
    label: str
    confidence: float = Field(ge=0.0, le=1.0)
    reason: str = ""


class EntityCandidate(BaseModel):
    name: str
    type: str = Field(description="PERSON | ORGANIZATION | PROJECT | LOCATION | TOPIC | OTHER")
    confidence: float = Field(ge=0.0, le=1.0)
    span_start: Optional[int] = None
    span_end: Optional[int] = None


class AnalyzeResponse(BaseModel):
    memory_id: Optional[str] = None
    normalized_text: str
    title_hint: str
    tags: List[str]
    entities: List[EntityCandidate]
    layer_hints: List[LayerHint]
    keywords: List[str]
    embedding: List[float]
    embedding_model: str
    embedding_dim: int


class UnderstandResponse(BaseModel):
    normalized_query: str
    intent: str = Field(description="RECALL_CONTENT | RECALL_FACT | RECALL_WHO | RECALL_WHEN | KEYWORD_LOOKUP | UNKNOWN")
    schema_hints: List[LayerHint]
    theme_hints: List[LayerHint]
    concept_hints: List[LayerHint]
    keywords: List[str]
    query_embedding: List[float]
    embedding_model: str
    embedding_dim: int


class VectorItem(BaseModel):
    id: Optional[str] = None
    text: str
    embedding: List[float]


class EmbedResponse(BaseModel):
    items: List[VectorItem]
    embedding_model: str
    embedding_dim: int


class HealthResponse(BaseModel):
    status: str
    embedding_backend: str
    embedding_model: str
    embedding_dim: Optional[int]
