package com.jinjin.memory.infrastructure.search;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.infrastructure.util.Json;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量倒排索引：reindex 时构建，search 时先收窄候选记忆 ID。
 */
public class KeywordIndex {
    private final Map<String, Set<String>> inverted;

    public KeywordIndex(Map<String, Set<String>> inverted) {
        this.inverted = inverted;
    }

    public static KeywordIndex build(List<MemoryEvent> records) {
        Map<String, Set<String>> inverted = new LinkedHashMap<>();
        for (MemoryEvent record : records) {
            if (record.deleted()) {
                continue;
            }
            LinkedHashSet<String> terms = new LinkedHashSet<>();
            terms.addAll(QueryTerms.from(record.title()).terms());
            terms.addAll(QueryTerms.from(record.content()).terms());
            for (String tag : record.tags()) {
                terms.add(TextNormalizer.normalize(tag));
            }
            for (String entity : record.entities()) {
                terms.add(TextNormalizer.normalize(entity));
            }
            if (!record.schemaLabel().isBlank()) {
                terms.add(TextNormalizer.normalize(record.schemaLabel()));
            }
            if (!record.themeLabel().isBlank()) {
                terms.add(TextNormalizer.normalize(record.themeLabel()));
            }
            if (!record.conceptLabel().isBlank()) {
                terms.add(TextNormalizer.normalize(record.conceptLabel()));
            }
            for (String term : terms) {
                if (term.length() < 2) {
                    continue;
                }
                inverted.computeIfAbsent(term, ignored -> new HashSet<>()).add(record.id());
            }
        }
        return new KeywordIndex(inverted);
    }

    public Set<String> candidateIds(String query, int maxIds) {
        QueryTerms terms = QueryTerms.from(query);
        Set<String> ids = new LinkedHashSet<>();
        for (String term : terms.terms()) {
            Set<String> hit = inverted.get(term);
            if (hit != null) {
                ids.addAll(hit);
            }
            if (ids.size() >= maxIds) {
                return ids;
            }
        }
        return ids;
    }

    public boolean isEmpty() {
        return inverted.isEmpty();
    }

    public long termCount() {
        return inverted.size();
    }

    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : inverted.entrySet()) {
            builder.append("{\"term\":\"")
                    .append(Json.escape(entry.getKey()))
                    .append("\",\"ids\":")
                    .append(Json.writeArray(List.copyOf(entry.getValue())))
                    .append("}")
                    .append(System.lineSeparator());
        }
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
    }

    public static KeywordIndex load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new KeywordIndex(Map.of());
        }
        Map<String, Set<String>> inverted = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String term = Json.readString(line, "term");
            List<String> ids = Json.readArray(line, "ids");
            inverted.put(term, new HashSet<>(ids));
        }
        return new KeywordIndex(inverted);
    }
}
