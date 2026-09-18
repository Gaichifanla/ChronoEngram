package com.jinjin.memory.infrastructure.semantic;

import java.util.List;

public record SemanticUnderstanding(
        String intent,
        List<LayerHint> schemaHints,
        List<LayerHint> themeHints,
        List<LayerHint> conceptHints,
        List<String> keywords,
        List<Double> queryEmbedding,
        String embeddingModel,
        int embeddingDim
) {
    public List<String> schemaLabels() {
        return schemaHints.stream().map(LayerHint::label).toList();
    }

    public List<String> themeLabels() {
        return themeHints.stream().map(LayerHint::label).toList();
    }

    public List<String> conceptLabels() {
        return conceptHints.stream().map(LayerHint::label).toList();
    }
}
