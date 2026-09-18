package com.jinjin.memory.application;

import com.jinjin.memory.domain.EventRelationType;
import com.jinjin.memory.domain.EventType;
import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.MemoryEventRelation;
import com.jinjin.memory.infrastructure.persistence.MemoryTimelineRepository;
import com.jinjin.memory.infrastructure.util.SnowflakeIdGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MemoryTimelineIndexer {
    private static final Comparator<MemoryEvent> KNOWLEDGE_ORDER = Comparator.comparing(MemoryEvent::learnedAt);

    private final MemoryTimelineRepository repository;
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultGenerator();

    public MemoryTimelineIndexer(MemoryTimelineRepository repository) {
        this.repository = repository;
    }

    public void index(MemoryEvent event, List<MemoryEvent> existingEvents) throws IOException {
        MemoryEvent previous = existingEvents.stream()
                .filter(existing -> !existing.deleted())
                .filter(existing -> existing.topicKey().equals(event.topicKey()))
                .max(KNOWLEDGE_ORDER)
                .orElse(null);
        if (previous != null) {
            repository.append(relation(previous, event));
        }
    }

    public int rebuild(List<MemoryEvent> events) throws IOException {
        Map<String, List<MemoryEvent>> byTopic = events.stream()
                .filter(event -> !event.deleted())
                .filter(event -> !event.topicKey().isBlank())
                .collect(Collectors.groupingBy(MemoryEvent::topicKey));
        List<MemoryEventRelation> relations = new ArrayList<>();
        for (List<MemoryEvent> timeline : byTopic.values()) {
            List<MemoryEvent> ordered = timeline.stream().sorted(KNOWLEDGE_ORDER).toList();
            for (int i = 1; i < ordered.size(); i++) {
                relations.add(relation(ordered.get(i - 1), ordered.get(i)));
            }
        }
        repository.replaceAll(relations);
        return relations.size();
    }

    public List<MemoryEventRelation> relations() throws IOException {
        return repository.loadRelations();
    }

    private MemoryEventRelation relation(MemoryEvent previous, MemoryEvent current) {
        return new MemoryEventRelation(
                idGenerator.nextIdString(), current.topicKey(), previous.id(), current.id(),
                relationType(current.eventType()), Math.min(previous.confidence(), current.confidence())
        );
    }

    private EventRelationType relationType(EventType type) {
        return switch (type) {
            case CONFIRMATION -> EventRelationType.CONFIRMS;
            case CORRECTION -> EventRelationType.CORRECTS;
            case UPDATE -> EventRelationType.UPDATES;
            case RECALL -> EventRelationType.RECALLS;
            default -> EventRelationType.SAME_TOPIC;
        };
    }

}
