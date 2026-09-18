"""Embedding：优先 sentence-transformers，失败时回退到确定性哈希向量。"""

from __future__ import annotations

import hashlib
import logging
from typing import TYPE_CHECKING

import numpy as np

from app.config import settings

if TYPE_CHECKING:
    from sentence_transformers import SentenceTransformer

logger = logging.getLogger(__name__)

_MODEL: SentenceTransformer | None = None
_BACKEND: str = "unknown"
_DIM: int = 384


def _hash_embedding(text: str, dim: int = 384) -> list[float]:
    """无模型时的确定性回退，仅用于联调；语义质量弱于真实 embedding。"""
    digest = hashlib.sha256(text.encode("utf-8")).digest()
    values = []
    while len(values) < dim:
        digest = hashlib.sha256(digest).digest()
        for byte in digest:
            values.append((byte / 255.0) * 2.0 - 1.0)
            if len(values) >= dim:
                break
    vec = np.array(values[:dim], dtype=np.float32)
    vec /= np.linalg.norm(vec) + 1e-8
    return vec.tolist()


def _load_transformer() -> SentenceTransformer | None:
    global _MODEL, _BACKEND, _DIM
    if settings.embedding_backend == "none":
        _BACKEND = "hash"
        _DIM = 384
        return None
    try:
        from sentence_transformers import SentenceTransformer

        model = SentenceTransformer(settings.embedding_model)
        _MODEL = model
        _BACKEND = "sentence-transformers"
        sample = model.encode(["probe"], normalize_embeddings=True)
        _DIM = int(sample.shape[1])
        logger.info("Loaded embedding model %s dim=%s", settings.embedding_model, _DIM)
        return model
    except Exception as exc:
        logger.warning("Failed to load embedding model, using hash fallback: %s", exc)
        _BACKEND = "hash"
        _DIM = 384
        return None


def ensure_ready() -> None:
    global _MODEL
    if _BACKEND == "unknown":
        _MODEL = _load_transformer()


def backend_name() -> str:
    ensure_ready()
    return _BACKEND


def model_name() -> str:
    if _BACKEND == "sentence-transformers":
        return settings.embedding_model
    return "hash-fallback-v1"


def embedding_dim() -> int:
    ensure_ready()
    return _DIM


def embed_texts(texts: list[str]) -> list[list[float]]:
    ensure_ready()
    cleaned = [t.strip()[: settings.max_text_length] for t in texts if t and t.strip()]
    if not cleaned:
        return []

    if _MODEL is not None:
        vectors = _MODEL.encode(cleaned, normalize_embeddings=True)
        return [v.tolist() for v in vectors]

    return [_hash_embedding(t, _DIM) for t in cleaned]
