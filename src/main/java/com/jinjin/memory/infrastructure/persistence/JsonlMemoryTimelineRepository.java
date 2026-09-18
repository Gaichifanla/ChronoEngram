package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.EventRelationType;
import com.jinjin.memory.domain.MemoryEventRelation;
import com.jinjin.memory.infrastructure.util.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class JsonlMemoryTimelineRepository implements MemoryTimelineRepository {
    private final Path relationsFile;

    public JsonlMemoryTimelineRepository(Path directory) {
        this.relationsFile = directory.resolve("relations.jsonl");
    }

    @Override
    public List<MemoryEventRelation> loadRelations() throws IOException {
        if (!Files.exists(relationsFile)) {
            return List.of();
        }
        List<MemoryEventRelation> relations = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(relationsFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    relations.add(fromJson(line));
                }
            }
        }
        return relations;
    }

    @Override
    public void append(MemoryEventRelation relation) throws IOException {
        ensureParent();
        Files.writeString(relationsFile, toJson(relation) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public void replaceAll(List<MemoryEventRelation> relations) throws IOException {
        ensureParent();
        try (BufferedWriter writer = Files.newBufferedWriter(relationsFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (MemoryEventRelation relation : relations) {
                writer.write(toJson(relation));
                writer.newLine();
            }
        }
    }

    private String toJson(MemoryEventRelation relation) {
        return "{" +
                "\"id\":\"" + Json.escape(relation.id()) + "\"," +
                "\"topicKey\":\"" + Json.escape(relation.topicKey()) + "\"," +
                "\"fromEventId\":\"" + Json.escape(relation.fromEventId()) + "\"," +
                "\"toEventId\":\"" + Json.escape(relation.toEventId()) + "\"," +
                "\"type\":\"" + relation.type() + "\"," +
                "\"confidence\":" + relation.confidence() + "}";
    }

    private MemoryEventRelation fromJson(String json) {
        return new MemoryEventRelation(
                Json.readString(json, "id"),
                Json.readString(json, "topicKey"),
                Json.readString(json, "fromEventId"),
                Json.readString(json, "toEventId"),
                EventRelationType.valueOf(Json.readString(json, "type")),
                Json.readDouble(json, "confidence", 1.0)
        );
    }

    private void ensureParent() throws IOException {
        Files.createDirectories(relationsFile.getParent());
    }
}
