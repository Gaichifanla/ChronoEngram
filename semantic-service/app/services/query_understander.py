from app.schemas.requests import UnderstandRequest
from app.schemas.responses import UnderstandResponse
from app.services import embedder
from app.services.entities import extract_entities
from app.services.intent import detect_intent
from app.services.keywords import extract_keywords
from app.services.layer_rules import concept_hints, schema_hints, theme_hints
from app.services.text_normalize import normalize


def understand(request: UnderstandRequest) -> UnderstandResponse:
    query = request.query.strip()
    normalized = normalize(query)
    entities = extract_entities(query)
    entity_names = [e.name for e in entities]
    keywords = extract_keywords(query)

    vectors = embedder.embed_texts([query])
    embedding = vectors[0] if vectors else []

    return UnderstandResponse(
        normalized_query=normalized,
        intent=detect_intent(query),
        schema_hints=schema_hints(query),
        theme_hints=theme_hints(query),
        concept_hints=concept_hints(query, entity_names, keywords),
        keywords=keywords,
        query_embedding=embedding,
        embedding_model=embedder.model_name(),
        embedding_dim=embedder.embedding_dim(),
    )
