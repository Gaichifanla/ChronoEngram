package com.jinjin.memory.domain;

import java.util.List;

public record SearchResult(
        MemoryEvent record,
        double score,
        String reason,
        String snippet,
        List<String> recallPath
) {
    public SearchResult(MemoryEvent record, double score, String reason, String snippet) {
        this(record, score, reason, snippet, List.of());
    }

    public String recallPathDisplay() {
        return recallPath.isEmpty() ? "" : String.join(" -> ", recallPath);
    }
}
