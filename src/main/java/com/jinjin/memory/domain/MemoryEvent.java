package com.jinjin.memory.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record MemoryEvent(
        String id,
        String topicKey,
        EventType eventType,
        String title,
        String content,
        List<String> tags,
        List<String> entities,
        String schemaLabel,
        String themeLabel,
        String conceptLabel,
        List<Double> embedding,
        TemporalRange occurredTime,
        Instant learnedAt,
        String source,
        double confidence,
        boolean deleted
) {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public String shortId() {
        return id;
    }

    public String displayOccurredTime() {
        return DISPLAY_TIME.format(occurredTime.start());
    }

    public String displayLearnedTime() {
        return DISPLAY_TIME.format(learnedAt);
    }

    public boolean hasEmbedding() {
        return embedding != null && !embedding.isEmpty();
    }

    public MemoryEvent withTopicKey(String value) {
        return new MemoryEvent(
                id, value, eventType, title, content, tags, entities,
                schemaLabel, themeLabel, conceptLabel, embedding,
                occurredTime, learnedAt, source, confidence, deleted
        );
    }
}
