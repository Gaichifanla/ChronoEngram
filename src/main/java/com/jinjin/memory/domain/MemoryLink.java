package com.jinjin.memory.domain;

import java.time.Instant;

public record MemoryLink(
        String id,
        String fromNodeId,
        String toNodeId,
        MemoryLinkType type,
        double weight,
        double confidence,
        Instant createdAt,
        Instant updatedAt
) {
}
