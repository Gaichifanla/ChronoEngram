package com.jinjin.memory.infrastructure.search;

import com.jinjin.memory.domain.MemoryLayer;
import com.jinjin.memory.domain.MemoryNode;
import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.SearchResult;
import com.jinjin.memory.infrastructure.persistence.MemoryGraphRepository;
import com.jinjin.memory.infrastructure.semantic.SemanticUnderstanding;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cascade Recall：沿 Schema → Theme → Concept → Trace 分层激活，返回带回忆路径的结果。
 */
public class HierarchicalRecallEngine {
    private static final int MAX_EXPAND_NODES = 24;

    private final MemoryGraphRepository graphRepository;

    public HierarchicalRecallEngine(MemoryGraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    public boolean hasGraph() throws IOException {
        return !graphRepository.loadNodes().isEmpty();
    }

    public List<SearchResult> recall(
            String query,
            List<MemoryEvent> records,
            int limit,
            Optional<SemanticUnderstanding> understanding
    ) throws IOException {
        List<MemoryNode> nodes = graphRepository.loadNodes();
        if (nodes.isEmpty()) {
            return List.of();
        }

        GraphIndex graph = new GraphIndex(nodes, graphRepository.loadLinks());
        Map<String, MemoryEvent> recordsById = new LinkedHashMap<>();
        for (MemoryEvent record : records) {
            recordsById.put(record.id(), record);
        }

        QueryTerms terms = QueryTerms.from(query);
        List<ScoredNode> scoredNodes = new ArrayList<>();
        for (MemoryNode node : nodes) {
            if (node.layer() == MemoryLayer.TRACE) {
                continue;
            }
            double score = scoreNode(node, terms, understanding);
            if (score > 0) {
                scoredNodes.add(new ScoredNode(node, score));
            }
        }
        scoredNodes.sort(Comparator.comparingDouble(ScoredNode::score).reversed());

        Map<String, SearchResult> hits = new LinkedHashMap<>();
        int expanded = 0;
        for (ScoredNode scored : scoredNodes) {
            if (expanded >= MAX_EXPAND_NODES) {
                break;
            }
            expanded++;
            for (MemoryNode trace : expandToTraces(graph, scored.node())) {
                MemoryEvent record = recordsById.get(trace.id());
                if (record == null) {
                    continue;
                }
                List<String> path = graph.recallPath(trace);
                double traceScore = scored.score() + layerBoost(trace.layer()) + path.size() * 0.4;
                String reason = "分层召回：" + String.join(" -> ", path);
                SearchResult candidate = new SearchResult(
                        record,
                        traceScore,
                        reason,
                        snippet(record.content(), terms),
                        path
                );
                hits.merge(record.id(), candidate, (left, right) -> right.score() > left.score() ? right : left);
            }
        }

        return hits.values().stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<MemoryNode> expandToTraces(GraphIndex graph, MemoryNode node) {
        return switch (node.layer()) {
            case TRACE -> List.of(node);
            case CONCEPT -> graph.tracesFor(node);
            case THEME -> {
                List<MemoryNode> traces = new ArrayList<>();
                for (MemoryNode concept : graph.children(node)) {
                    traces.addAll(graph.tracesFor(concept));
                }
                yield traces;
            }
            case SCHEMA -> {
                List<MemoryNode> traces = new ArrayList<>();
                for (MemoryNode theme : graph.children(node)) {
                    for (MemoryNode concept : graph.children(theme)) {
                        traces.addAll(graph.tracesFor(concept));
                    }
                }
                yield traces;
            }
            default -> List.of();
        };
    }

    private double scoreNode(MemoryNode node, QueryTerms terms, Optional<SemanticUnderstanding> understanding) {
        double score = 0;
        String label = TextNormalizer.normalize(node.label());
        String summary = TextNormalizer.normalize(node.summary());
        String normalizedQuery = TextNormalizer.normalize(terms.original());

        if (!normalizedQuery.isBlank() && (label.contains(normalizedQuery) || summary.contains(normalizedQuery))) {
            score += 6;
        }
        for (String term : terms.terms()) {
            if (label.contains(term) || summary.contains(term)) {
                score += 3;
            }
            if (node.keywords().stream().map(TextNormalizer::normalize).anyMatch(k -> k.contains(term))) {
                score += 2;
            }
        }

        if (understanding.isPresent()) {
            SemanticUnderstanding ctx = understanding.get();
            score += hintBoost(node, ctx.schemaLabels(), 3.5);
            score += hintBoost(node, ctx.themeLabels(), 3.0);
            score += hintBoost(node, ctx.conceptLabels(), 4.0);
        }
        return score * layerBoost(node.layer());
    }

    private double hintBoost(MemoryNode node, List<String> hints, double weight) {
        for (String hint : hints) {
            if (node.label().equals(hint)) {
                return weight;
            }
        }
        return 0;
    }

    private double layerBoost(MemoryLayer layer) {
        return switch (layer) {
            case SCHEMA -> 1.0;
            case THEME -> 1.1;
            case CONCEPT -> 1.25;
            case TRACE -> 1.15;
            default -> 1.0;
        };
    }

    private String snippet(String content, QueryTerms terms) {
        String normalizedContent = TextNormalizer.normalize(content);
        for (String term : terms.terms()) {
            int index = normalizedContent.indexOf(term);
            if (index >= 0) {
                int start = Math.max(0, index - 25);
                int end = Math.min(content.length(), index + 65);
                String prefix = start > 0 ? "..." : "";
                String suffix = end < content.length() ? "..." : "";
                return prefix + content.substring(start, end) + suffix;
            }
        }
        return content.length() <= 90 ? content : content.substring(0, 90) + "...";
    }

    private record ScoredNode(MemoryNode node, double score) {
    }
}
