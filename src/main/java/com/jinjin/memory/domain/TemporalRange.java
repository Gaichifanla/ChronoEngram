package com.jinjin.memory.domain;

import java.time.Instant;

public record TemporalRange(
        Instant start,
        Instant end,
        TemporalPrecision precision,
        String originalExpression,
        double confidence
) {
    public static TemporalRange point(Instant instant, TemporalPrecision precision, String expression, double confidence) {
        return new TemporalRange(instant, instant, precision, expression, confidence);
    }
}
