package com.jinjin.memory.domain;

public record MemoryEventRelation(
        String id,
        String topicKey,
        String fromEventId,
        String toEventId,
        EventRelationType type,
        double confidence
) {
}
