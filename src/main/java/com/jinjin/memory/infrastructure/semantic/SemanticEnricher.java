package com.jinjin.memory.infrastructure.semantic;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.TemporalRange;
import com.jinjin.memory.infrastructure.tagging.AutoTagger;
import com.jinjin.memory.infrastructure.temporal.EventClassifier;
import com.jinjin.memory.infrastructure.util.TitleGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 优先使用 Python 语义侧车；不可用时回退到本地 {@link AutoTagger}。
 */
public class SemanticEnricher {
    private final Optional<SemanticServiceClient> client;
    private final AutoTagger fallback = new AutoTagger();
    private final EventClassifier eventClassifier = new EventClassifier();

    public SemanticEnricher(Optional<SemanticServiceClient> client) {
        this.client = client;
    }

    public MemoryEvent enrich(String id, String content, TemporalRange occurredTime, Instant learnedAt) {
        Optional<SemanticAnalysis> analysis = client.flatMap(c -> c.analyze(content, id));
        if (analysis.isPresent()) {
            SemanticAnalysis a = analysis.get();
            String title = a.titleHint().isBlank() ? TitleGenerator.title(content) : a.titleHint();
            List<String> tags = a.tags().isEmpty() ? fallback.tags(content) : a.tags();
            List<String> entities = a.entities().isEmpty() ? fallback.entities(content) : a.entities();
            return new MemoryEvent(
                    id,
                    "",
                    eventClassifier.classify(content),
                    title,
                    content,
                    tags,
                    entities,
                    a.schemaLabel(),
                    a.themeLabel(),
                    a.conceptLabel(),
                    a.embedding(),
                    occurredTime,
                    learnedAt,
                    "manual",
                    occurredTime.confidence(),
                    false
            );
        }
        return new MemoryEvent(
                id,
                "",
                eventClassifier.classify(content),
                TitleGenerator.title(content),
                content,
                fallback.tags(content),
                fallback.entities(content),
                "",
                "",
                "",
                List.of(),
                occurredTime,
                learnedAt,
                "manual",
                occurredTime.confidence(),
                false
        );
    }
}
