package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.EventType;
import com.jinjin.memory.domain.TemporalPrecision;
import com.jinjin.memory.domain.TemporalRange;
import com.jinjin.memory.infrastructure.util.Json;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonlMemoryRepository implements MemoryRepository {
    private static final Pattern ABOUT = Pattern.compile("关于([^，。,.]{2,16})的");
    private final Path dataFile;

    public JsonlMemoryRepository(Path dataFile) {
        this.dataFile = dataFile;
    }

    @Override
    public void append(MemoryEvent record) throws IOException {
        appendAll(List.of(record));
    }

    @Override
    public void appendAll(List<MemoryEvent> records) throws IOException {
        ensureDataFile();
        StringBuilder builder = new StringBuilder();
        for (MemoryEvent record : records) {
            builder.append(toJsonLine(record)).append(System.lineSeparator());
        }
        Files.writeString(dataFile, builder.toString(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    @Override
    public List<MemoryEvent> load() throws IOException {
        if (!Files.exists(dataFile)) {
            return List.of();
        }

        List<MemoryEvent> records = new ArrayList<>();
        for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                records.add(fromJsonLine(line));
            }
        }
        return records;
    }

    @Override
    public void rewrite(List<MemoryEvent> records) throws IOException {
        ensureDataFile();
        StringBuilder builder = new StringBuilder();
        for (MemoryEvent record : records) {
            builder.append(toJsonLine(record)).append(System.lineSeparator());
        }
        Files.writeString(dataFile, builder.toString(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void ensureDataFile() throws IOException {
        Path parent = dataFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String toJsonLine(MemoryEvent record) {
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"id\":\"").append(Json.escape(record.id())).append("\",");
        builder.append("\"topicKey\":\"").append(Json.escape(record.topicKey())).append("\",");
        builder.append("\"eventType\":\"").append(record.eventType()).append("\",");
        builder.append("\"title\":\"").append(Json.escape(record.title())).append("\",");
        builder.append("\"content\":\"").append(Json.escape(record.content())).append("\",");
        builder.append("\"tags\":").append(Json.writeArray(record.tags())).append(",");
        builder.append("\"entities\":").append(Json.writeArray(record.entities())).append(",");
        if (record.schemaLabel() != null && !record.schemaLabel().isBlank()) {
            builder.append("\"schemaLabel\":\"").append(Json.escape(record.schemaLabel())).append("\",");
        }
        if (record.themeLabel() != null && !record.themeLabel().isBlank()) {
            builder.append("\"themeLabel\":\"").append(Json.escape(record.themeLabel())).append("\",");
        }
        if (record.conceptLabel() != null && !record.conceptLabel().isBlank()) {
            builder.append("\"conceptLabel\":\"").append(Json.escape(record.conceptLabel())).append("\",");
        }
        if (record.hasEmbedding()) {
            builder.append("\"embedding\":").append(Json.writeNumberArray(record.embedding())).append(",");
        }
        builder.append("\"occurredStart\":\"").append(record.occurredTime().start()).append("\",");
        builder.append("\"occurredEnd\":\"").append(record.occurredTime().end()).append("\",");
        builder.append("\"occurredPrecision\":\"").append(record.occurredTime().precision()).append("\",");
        builder.append("\"occurredExpression\":\"").append(Json.escape(record.occurredTime().originalExpression())).append("\",");
        builder.append("\"occurredConfidence\":").append(record.occurredTime().confidence()).append(",");
        builder.append("\"learnedAt\":\"").append(record.learnedAt()).append("\",");
        builder.append("\"source\":\"").append(Json.escape(record.source())).append("\",");
        builder.append("\"confidence\":").append(record.confidence()).append(",");
        builder.append("\"deleted\":").append(record.deleted());
        builder.append("}");
        return builder.toString();
    }

    private MemoryEvent fromJsonLine(String json) {
        String learnedText = Json.readString(json, "learnedAt");
        boolean legacy = learnedText.isBlank();
        if (legacy) {
            learnedText = Json.readString(json, "createdAt");
        }
        Instant learnedAt = Instant.parse(learnedText);
        String occurredStartText = Json.readString(json, "occurredStart");
        Instant occurredStart = occurredStartText.isBlank() ? learnedAt : Instant.parse(occurredStartText);
        String occurredEndText = Json.readString(json, "occurredEnd");
        Instant occurredEnd = occurredEndText.isBlank() ? occurredStart : Instant.parse(occurredEndText);
        String precisionText = Json.readString(json, "occurredPrecision");
        TemporalPrecision precision = precisionText.isBlank() ? TemporalPrecision.INFERRED : TemporalPrecision.valueOf(precisionText);
        String eventTypeText = Json.readString(json, "eventType");
        EventType eventType = eventTypeText.isBlank() ? EventType.OBSERVATION : EventType.valueOf(eventTypeText);
        String topicKey = Json.readString(json, "topicKey");
        if (topicKey.isBlank()) {
            topicKey = legacyTopicKey(json);
        }
        return new MemoryEvent(
                Json.readString(json, "id"),
                topicKey,
                eventType,
                Json.readString(json, "title"),
                Json.readString(json, "content"),
                Json.readArray(json, "tags"),
                Json.readArray(json, "entities"),
                Json.readString(json, "schemaLabel"),
                Json.readString(json, "themeLabel"),
                Json.readString(json, "conceptLabel"),
                Json.readNumberArray(json, "embedding"),
                new TemporalRange(
                        occurredStart,
                        occurredEnd,
                        precision,
                        Json.readString(json, "occurredExpression"),
                        Json.readDouble(json, "occurredConfidence", legacy ? 0.35 : 0.55)
                ),
                learnedAt,
                legacy ? "legacy" : Json.readString(json, "source"),
                Json.readDouble(json, "confidence", legacy ? 0.35 : 1.0),
                Json.readBoolean(json, "deleted")
        );
    }

    private String legacyTopicKey(String json) {
        String concept = Json.readString(json, "conceptLabel");
        if (!concept.isBlank()) {
            return TextNormalizer.normalize(concept);
        }
        List<String> entities = Json.readArray(json, "entities");
        if (!entities.isEmpty()) {
            return TextNormalizer.normalize(entities.getFirst());
        }
        Matcher about = ABOUT.matcher(Json.readString(json, "content"));
        if (about.find()) {
            return TextNormalizer.normalize(about.group(1));
        }
        return TextNormalizer.normalize(Json.readString(json, "title").replace("...", ""));
    }
}
