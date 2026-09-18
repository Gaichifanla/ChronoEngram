package com.jinjin.memory.infrastructure.search;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.SearchResult;
import com.jinjin.memory.infrastructure.semantic.SemanticUnderstanding;
import com.jinjin.memory.infrastructure.util.TextNormalizer;
import com.jinjin.memory.infrastructure.util.VectorMath;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SearchEngine {
    public List<SearchResult> search(String query, List<MemoryEvent> records, int limit) {
        return search(query, records, limit, Optional.empty());
    }

    public List<SearchResult> search(
            String query,
            List<MemoryEvent> records,
            int limit,
            Optional<SemanticUnderstanding> understanding
    ) {
        QueryTerms terms = QueryTerms.from(query);
        return records.stream()
                .map(record -> score(record, terms, understanding))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparing(SearchResult::score).reversed()
                        .thenComparing(result -> result.record().learnedAt(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private SearchResult score(
            MemoryEvent record,
            QueryTerms terms,
            Optional<SemanticUnderstanding> understanding
    ) {
        double score = 0;
        List<String> reasons = new ArrayList<>();
        String normalizedContent = TextNormalizer.normalize(record.content());
        String normalizedTitle = TextNormalizer.normalize(record.title());
        String normalizedQuery = TextNormalizer.normalize(terms.original());

        if (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery)) {
            score += 8;
            reasons.add("原文完整命中");
        }
        if (!normalizedQuery.isBlank() && normalizedTitle.contains(normalizedQuery)) {
            score += 6;
            reasons.add("标题完整命中");
        }

        for (String term : terms.terms()) {
            boolean matched = false;
            if (normalizedTitle.contains(term)) {
                score += 4;
                matched = true;
            }
            if (record.tags().stream().map(TextNormalizer::normalize).anyMatch(tag -> tag.contains(term) || term.contains(tag))) {
                score += 3;
                matched = true;
            }
            if (record.entities().stream().map(TextNormalizer::normalize).anyMatch(entity -> entity.contains(term) || term.contains(entity))) {
                score += 3;
                matched = true;
            }
            if (normalizedContent.contains(term)) {
                score += Math.max(1.0, Math.min(3.0, term.length() / 2.0));
                matched = true;
            }
            if (matched) {
                reasons.add("命中“" + term + "”");
            }
        }

        if (understanding.isPresent()) {
            SemanticUnderstanding ctx = understanding.get();
            score += layerBoost(record.schemaLabel(), ctx.schemaLabels(), 3.5, "Schema", reasons);
            score += layerBoost(record.themeLabel(), ctx.themeLabels(), 3.0, "Theme", reasons);
            score += layerBoost(record.conceptLabel(), ctx.conceptLabels(), 4.0, "Concept", reasons);

            for (String schema : ctx.schemaLabels()) {
                if (matchesLabel(record, schema)) {
                    score += 2.0;
                    reasons.add("语义路径Schema「" + schema + "」");
                }
            }
            for (String theme : ctx.themeLabels()) {
                if (record.themeLabel().equals(theme) || record.tags().contains(theme)) {
                    score += 2.0;
                    reasons.add("语义路径Theme「" + theme + "」");
                }
            }
            for (String concept : ctx.conceptLabels()) {
                if (matchesLabel(record, concept) || record.entities().contains(concept)) {
                    score += 2.5;
                    reasons.add("语义路径Concept「" + concept + "」");
                }
            }

            if (!ctx.queryEmbedding().isEmpty() && record.hasEmbedding()) {
                double similarity = VectorMath.cosineSimilarity(ctx.queryEmbedding(), record.embedding());
                if (similarity > 0.35) {
                    score += similarity * 10.0;
                    reasons.add(String.format("语义相似度 %.2f", similarity));
                }
            }
        }

        if (score > 0) {
            score += recencyBoost(record.learnedAt());
        }
        return new SearchResult(record, score, reason(reasons), snippet(record.content(), terms));
    }

    private double layerBoost(String recordLabel, List<String> hints, double weight, String layerName, List<String> reasons) {
        if (recordLabel == null || recordLabel.isBlank() || hints.isEmpty()) {
            return 0;
        }
        for (String hint : hints) {
            if (recordLabel.equals(hint)) {
                reasons.add(layerName + "分层命中「" + hint + "」");
                return weight;
            }
        }
        return 0;
    }

    private boolean matchesLabel(MemoryEvent record, String label) {
        String normalized = TextNormalizer.normalize(label);
        return TextNormalizer.normalize(record.schemaLabel()).contains(normalized)
                || TextNormalizer.normalize(record.themeLabel()).contains(normalized)
                || TextNormalizer.normalize(record.conceptLabel()).contains(normalized)
                || record.tags().stream().map(TextNormalizer::normalize).anyMatch(tag -> tag.contains(normalized))
                || TextNormalizer.normalize(record.content()).contains(normalized);
    }

    private double recencyBoost(Instant learnedAt) {
        long ageSeconds = Math.max(1, Instant.now().getEpochSecond() - learnedAt.getEpochSecond());
        return Math.max(0.0, 1.0 - Math.log10(ageSeconds) / 8.0);
    }

    private String reason(List<String> reasons) {
        if (reasons.isEmpty()) {
            return "近期记忆弱匹配";
        }
        return String.join("、", reasons.stream().distinct().limit(6).toList());
    }

    private String snippet(String content, QueryTerms terms) {
        int index = -1;
        String normalizedContent = TextNormalizer.normalize(content);
        for (String term : terms.terms()) {
            index = normalizedContent.indexOf(term);
            if (index >= 0) {
                break;
            }
        }
        if (index < 0) {
            return content.length() <= 90 ? content : content.substring(0, 90) + "...";
        }
        int start = Math.max(0, index - 25);
        int end = Math.min(content.length(), index + 65);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";
        return prefix + content.substring(start, end) + suffix;
    }
}
