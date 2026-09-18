from app.schemas.requests import AnalyzeRequest
from app.schemas.responses import AnalyzeResponse, LayerHint
from app.services import embedder
from app.services.entities import extract_entities
from app.services.keywords import extract_keywords, title_hint
from app.services.layer_rules import concept_hints, schema_hints, tags, theme_hints
from app.services.text_normalize import normalize


def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    text = request.text.strip()
    normalized = normalize(text)
    entities = extract_entities(text)
    entity_names = [e.name for e in entities]
    keywords = extract_keywords(text)
    tag_list = tags(text)

    schemas = schema_hints(text)
    themes = theme_hints(text)
    concepts = concept_hints(text, entity_names, keywords)

    layer_hints: list[LayerHint] = []
    layer_hints.extend(schemas)
    layer_hints.extend(themes)
    layer_hints.extend(concepts)

    vectors = embedder.embed_texts([text])
    embedding = vectors[0] if vectors else []

    return AnalyzeResponse(
        memory_id=request.memory_id,
        normalized_text=normalized,
        title_hint=title_hint(text),
        tags=tag_list,
        entities=entities,
        layer_hints=layer_hints,
        keywords=keywords,
        embedding=embedding,
        embedding_model=embedder.model_name(),
        embedding_dim=embedder.embedding_dim(),
    )
