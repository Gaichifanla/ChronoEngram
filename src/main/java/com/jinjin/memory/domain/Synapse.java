package com.jinjin.memory.domain;

import java.time.Instant;

public record Synapse(
        String fromUnitId,
        String toUnitId,
        double weight,
        long coActivationCount,
        Instant lastStrengthenedAt
) {
}
