package com.jinjin.memory.domain;

import java.time.Instant;
import java.util.List;

public record MemoryEngram(
        String id,
        String traceId,
        List<String> unitIds,
        List<Double> strengths,
        Instant encodedAt
) {
}
