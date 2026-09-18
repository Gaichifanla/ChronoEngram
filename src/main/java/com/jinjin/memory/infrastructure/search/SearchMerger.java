package com.jinjin.memory.infrastructure.search;

import com.jinjin.memory.domain.SearchResult;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchMerger {
    private SearchMerger() {
    }

    public static List<SearchResult> merge(List<SearchResult> keywordHits, List<SearchResult> graphHits, int limit) {
        Map<String, SearchResult> merged = new LinkedHashMap<>();
        for (SearchResult result : keywordHits) {
            merged.put(result.record().id(), result);
        }
        for (SearchResult graph : graphHits) {
            merged.merge(graph.record().id(), graph, SearchMerger::combine);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private static SearchResult combine(SearchResult keyword, SearchResult graph) {
        double score = keyword.score() + graph.score() * 0.85;
        String reason = keyword.reason();
        if (!graph.reason().isBlank()) {
            reason = reason.isBlank() ? graph.reason() : reason + "；" + graph.reason();
        }
        List<String> path = graph.recallPath().isEmpty() ? keyword.recallPath() : graph.recallPath();
        String snippet = keyword.snippet().length() >= graph.snippet().length() ? keyword.snippet() : graph.snippet();
        return new SearchResult(keyword.record(), score, reason, snippet, path);
    }
}
