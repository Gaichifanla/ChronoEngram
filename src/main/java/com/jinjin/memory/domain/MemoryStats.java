package com.jinjin.memory.domain;

public record MemoryStats(
        long totalRecords,
        long activeRecords,
        long deletedRecords,
        long withEmbedding,
        long graphNodes,
        long graphLinks,
        long graphTraces,
        long graphConcepts,
        long graphThemes,
        long graphSchemas,
        boolean keywordIndexReady,
        long keywordIndexTerms,
        boolean semanticServiceUp,
        long associativeUnits,
        long associativeSynapses,
        long associativeEngrams
) {
}
