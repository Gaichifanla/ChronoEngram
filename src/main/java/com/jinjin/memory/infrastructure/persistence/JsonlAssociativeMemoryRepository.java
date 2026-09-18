package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryEngram;
import com.jinjin.memory.domain.MemoryUnit;
import com.jinjin.memory.domain.MemoryUnitType;
import com.jinjin.memory.domain.Synapse;
import com.jinjin.memory.infrastructure.util.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JsonlAssociativeMemoryRepository implements AssociativeMemoryRepository {
    private final Path unitsFile;
    private final Path synapsesFile;
    private final Path engramsFile;

    public JsonlAssociativeMemoryRepository(Path directory) {
        this.unitsFile = directory.resolve("units.jsonl");
        this.synapsesFile = directory.resolve("synapses.jsonl");
        this.engramsFile = directory.resolve("engrams.jsonl");
    }

    @Override
    public List<MemoryUnit> loadUnits() throws IOException {
        return readLines(unitsFile, this::unitFromJson);
    }

    @Override
    public List<Synapse> loadSynapses() throws IOException {
        return readLines(synapsesFile, this::synapseFromJson);
    }

    @Override
    public List<MemoryEngram> loadEngrams() throws IOException {
        return readLines(engramsFile, this::engramFromJson);
    }

    @Override
    public void replaceAll(List<MemoryUnit> units, List<Synapse> synapses, List<MemoryEngram> engrams) throws IOException {
        writeLines(unitsFile, units.stream().map(this::toJson).toList());
        writeLines(synapsesFile, synapses.stream().map(this::toJson).toList());
        writeLines(engramsFile, engrams.stream().map(this::toJson).toList());
    }

    @Override
    public void savePlasticState(List<MemoryUnit> units, List<Synapse> synapses) throws IOException {
        writeLines(unitsFile, units.stream().map(this::toJson).toList());
        writeLines(synapsesFile, synapses.stream().map(this::toJson).toList());
    }

    @Override
    public void appendEngrams(List<MemoryEngram> engrams) throws IOException {
        ensureParent(engramsFile);
        try (BufferedWriter writer = Files.newBufferedWriter(
                engramsFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            for (MemoryEngram engram : engrams) {
                writer.write(toJson(engram));
                writer.newLine();
            }
        }
    }

    private <T> List<T> readLines(Path file, LineParser<T> parser) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<T> values = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    values.add(parser.parse(line));
                }
            }
        }
        return values;
    }

    private void writeLines(Path file, List<String> lines) throws IOException {
        ensureParent(file);
        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private void ensureParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String toJson(MemoryUnit unit) {
        return "{" +
                "\"id\":\"" + Json.escape(unit.id()) + "\"," +
                "\"type\":\"" + unit.type() + "\"," +
                "\"label\":\"" + Json.escape(unit.label()) + "\"," +
                "\"occurrenceCount\":" + unit.occurrenceCount() + "," +
                "\"activationCount\":" + unit.activationCount() + "," +
                "\"stability\":" + unit.stability() + "," +
                "\"createdAt\":\"" + unit.createdAt() + "\"," +
                "\"lastActivatedAt\":\"" + unit.lastActivatedAt() + "\"" +
                "}";
    }

    private String toJson(Synapse synapse) {
        return "{" +
                "\"fromUnitId\":\"" + Json.escape(synapse.fromUnitId()) + "\"," +
                "\"toUnitId\":\"" + Json.escape(synapse.toUnitId()) + "\"," +
                "\"weight\":" + synapse.weight() + "," +
                "\"coActivationCount\":" + synapse.coActivationCount() + "," +
                "\"lastStrengthenedAt\":\"" + synapse.lastStrengthenedAt() + "\"" +
                "}";
    }

    private String toJson(MemoryEngram engram) {
        return "{" +
                "\"id\":\"" + Json.escape(engram.id()) + "\"," +
                "\"traceId\":\"" + Json.escape(engram.traceId()) + "\"," +
                "\"unitIds\":" + Json.writeArray(engram.unitIds()) + "," +
                "\"strengths\":" + Json.writeNumberArray(engram.strengths()) + "," +
                "\"encodedAt\":\"" + engram.encodedAt() + "\"" +
                "}";
    }

    private MemoryUnit unitFromJson(String json) {
        return new MemoryUnit(
                Json.readString(json, "id"),
                MemoryUnitType.valueOf(Json.readString(json, "type")),
                Json.readString(json, "label"),
                Json.readLong(json, "occurrenceCount", 0),
                Json.readLong(json, "activationCount", 0),
                Json.readDouble(json, "stability", 0.5),
                Instant.parse(Json.readString(json, "createdAt")),
                Instant.parse(Json.readString(json, "lastActivatedAt"))
        );
    }

    private Synapse synapseFromJson(String json) {
        return new Synapse(
                Json.readString(json, "fromUnitId"),
                Json.readString(json, "toUnitId"),
                Json.readDouble(json, "weight", 0.1),
                Json.readLong(json, "coActivationCount", 0),
                Instant.parse(Json.readString(json, "lastStrengthenedAt"))
        );
    }

    private MemoryEngram engramFromJson(String json) {
        String encodedAt = Json.readString(json, "encodedAt");
        if (encodedAt.isBlank()) {
            encodedAt = Json.readString(json, "createdAt");
        }
        return new MemoryEngram(
                Json.readString(json, "id"),
                Json.readString(json, "traceId"),
                Json.readArray(json, "unitIds"),
                Json.readNumberArray(json, "strengths"),
                Instant.parse(encodedAt)
        );
    }

    @FunctionalInterface
    private interface LineParser<T> {
        T parse(String line);
    }
}
