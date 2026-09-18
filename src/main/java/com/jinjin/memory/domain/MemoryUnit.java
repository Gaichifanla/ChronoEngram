package com.jinjin.memory.domain;

import java.time.Instant;

public record MemoryUnit(
        String id,
        MemoryUnitType type,
        String label,
        long occurrenceCount,
        long activationCount,
        double stability,
        Instant createdAt,
        Instant lastActivatedAt
) {
}
