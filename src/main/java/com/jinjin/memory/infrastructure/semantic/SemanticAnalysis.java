package com.jinjin.memory.infrastructure.semantic;

import java.util.List;

public record SemanticAnalysis(
        String titleHint,
        List<String> tags,
        List<String> entities,
        List<LayerHint> layerHints,
        List<String> keywords,
        List<Double> embedding,
        String embeddingModel,
        int embeddingDim
) {
    public String schemaLabel() {
        return firstLabel("SCHEMA");
    }

    public String themeLabel() {
        return firstLabel("THEME");
    }

    public String conceptLabel() {
        return firstLabel("CONCEPT");
    }

    private String firstLabel(String layer) {
        return layerHints.stream()
                .filter(hint -> layer.equals(hint.layer()))
                .map(LayerHint::label)
                .findFirst()
                .orElse("");
    }
}
