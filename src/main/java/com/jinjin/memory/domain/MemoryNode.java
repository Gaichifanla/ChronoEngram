package com.jinjin.memory.domain;

import java.time.Instant;
import java.util.List;

public record MemoryNode(
        String id,
        MemoryLayer layer,
        String label,
        String summary,
        String content,
        List<String> keywords,
        List<String> entities,
        double confidence,
        int importance,
        Instant createdAt,
        Instant updatedAt,
        Instant lastActivatedAt,
        long activationCount
) {
}
