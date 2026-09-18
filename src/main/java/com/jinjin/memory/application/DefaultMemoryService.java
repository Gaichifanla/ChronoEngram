package com.jinjin.memory.application;

import com.jinjin.memory.domain.MemoryLayer;
import com.jinjin.memory.domain.MemoryNode;
import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.MemoryStats;
import com.jinjin.memory.domain.SearchResult;
import com.jinjin.memory.domain.AssociativeRecallResult;
import com.jinjin.memory.domain.AssociativeMemoryStats;
import com.jinjin.memory.domain.MemoryEventRelation;
import com.jinjin.memory.domain.TimelineEntry;
import com.jinjin.memory.domain.EventType;
import com.jinjin.memory.domain.TemporalPrecision;
import com.jinjin.memory.domain.TemporalRange;
import com.jinjin.memory.infrastructure.associative.AssociativeMemoryEngine;
import com.jinjin.memory.infrastructure.persistence.MemoryGraphRepository;
import com.jinjin.memory.infrastructure.persistence.MemoryRepository;
import com.jinjin.memory.infrastructure.temporal.EventTemporalParser;
import com.jinjin.memory.infrastructure.search.HierarchicalRecallEngine;
import com.jinjin.memory.infrastructure.search.KeywordIndex;
import com.jinjin.memory.infrastructure.search.SearchEngine;
import com.jinjin.memory.infrastructure.search.SearchMerger;
import com.jinjin.memory.infrastructure.semantic.EmbedItem;
import com.jinjin.memory.infrastructure.semantic.SemanticEnricher;
import com.jinjin.memory.infrastructure.semantic.SemanticServiceClient;
import com.jinjin.memory.infrastructure.semantic.SemanticUnderstanding;
import com.jinjin.memory.infrastructure.tagging.AutoTagger;
import com.jinjin.memory.infrastructure.util.SnowflakeIdGenerator;
import com.jinjin.memory.infrastructure.util.TitleGenerator;
import com.jinjin.memory.infrastructure.util.TextNormalizer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultMemoryService implements MemoryCommandService, MemoryQueryService {
    private static final int EMBED_BATCH_SIZE = 32;
    private static final int SEARCH_CANDIDATE_CAP = 6000;
    private static final int SEARCH_RECENT_FALLBACK = 4000;

    private final MemoryRepository repository;
    private final MemoryGraphRepository graphRepository;
    private final MemoryIndexer indexer;
    private final MemoryTimelineIndexer timelineIndexer;
    private final EventTemporalParser temporalParser;
    private final TopicKeyResolver topicKeyResolver;
    private final SemanticEnricher enricher;
    private final Optional<SemanticServiceClient> semanticClient;
    private final SearchEngine searchEngine;
    private final HierarchicalRecallEngine recallEngine;
    private final AssociativeMemoryEngine associativeMemory;
    private final Path keywordIndexFile;
    private final AutoTagger fallbackTagger = new AutoTagger();
    private final SnowflakeIdGenerator idGenerator;

    private KeywordIndex keywordIndex = new KeywordIndex(Map.of());

    public DefaultMemoryService(
            MemoryRepository repository,
            MemoryGraphRepository graphRepository,
            MemoryIndexer indexer,
            MemoryTimelineIndexer timelineIndexer,
            EventTemporalParser temporalParser,
            TopicKeyResolver topicKeyResolver,
            SemanticEnricher enricher,
            Optional<SemanticServiceClient> semanticClient,
            SearchEngine searchEngine,
            HierarchicalRecallEngine recallEngine,
            AssociativeMemoryEngine associativeMemory,
            Path keywordIndexFile
    ) throws IOException {
        this.repository = repository;
        this.graphRepository = graphRepository;
        this.indexer = indexer;
        this.timelineIndexer = timelineIndexer;
        this.temporalParser = temporalParser;
        this.topicKeyResolver = topicKeyResolver;
        this.enricher = enricher;
        this.semanticClient = semanticClient;
        this.searchEngine = searchEngine;
        this.recallEngine = recallEngine;
        this.associativeMemory = associativeMemory;
        this.keywordIndexFile = keywordIndexFile;
        this.keywordIndex = KeywordIndex.load(keywordIndexFile);
        this.idGenerator = SnowflakeIdGenerator.defaultGenerator();
    }

    @Override
    public MemoryEvent add(String content) throws IOException {
        String cleaned = content.trim();
        Instant learnedAt = Instant.now();
        MemoryEvent draft = enricher.enrich(
                idGenerator.nextIdString(), cleaned, temporalParser.parse(cleaned, learnedAt), learnedAt
        );
        MemoryEvent record = draft.withTopicKey(topicKeyResolver.resolve(draft));
        List<MemoryEvent> existing = list();
        repository.append(record);
        timelineIndexer.index(record, existing);
        indexer.index(record);
        associativeMemory.learn(record);
        return record;
    }

    @Override
    public int seedBasicKnowledge(int count) throws IOException {
        List<MemoryEvent> records = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            records.add(createSeedRecord(BasicKnowledgeGenerator.content(i)));
        }
        repository.appendAll(records);
        indexer.index(records);
        associativeMemory.learn(records);
        refreshKeywordIndex();
        return records.size();
    }

    @Override
    public int reindexGraph() throws IOException {
        List<MemoryEvent> records = list();
        int total = records.size();
        System.out.printf("开始重建分层图，共 %d 条记忆...%n", total);
        long started = System.currentTimeMillis();

        indexer.rebuild(records, (done, all) -> {
            if (done == all || done % 1000 == 0) {
                System.out.printf("  建图进度 %d / %d%n", done, all);
            }
        });

        System.out.println("正在重建主题时间轴...");
        int timelineRelations = timelineIndexer.rebuild(records);

        System.out.println("正在构建关键词倒排索引...");
        keywordIndex = KeywordIndex.build(records);
        keywordIndex.save(keywordIndexFile);

        long elapsed = System.currentTimeMillis() - started;
        System.out.printf(
                "完成：节点 %d，边 %d，时间轴关系 %d，索引词条 %d，耗时 %.1fs%n",
                graphRepository.loadNodes().size(),
                graphRepository.loadLinks().size(),
                timelineRelations,
                keywordIndex.termCount(),
                elapsed / 1000.0
        );
        return records.size();
    }

    @Override
    public int rebuildAssociativeMemory() throws IOException {
        List<MemoryEvent> records = list();
        System.out.printf("开始重建联想记忆网络，共 %d 条记忆...%n", records.size());
        long started = System.currentTimeMillis();
        int learned = associativeMemory.rebuild(records, (done, all) -> {
            if (done == all || done % 10_000 == 0) {
                System.out.printf("  学习进度 %d / %d%n", done, all);
            }
        });
        System.out.printf("联想记忆网络完成，Engram %d，耗时 %.1fs%n", learned, (System.currentTimeMillis() - started) / 1000.0);
        return learned;
    }

    @Override
    public int backfillEmbeddings(int limit, boolean force) throws IOException {
        if (semanticClient.isEmpty()) {
            throw new IllegalStateException("语义侧车未启用，无法补向量。请启动 semantic-service 并勿设置 JINJIN_SEMANTIC_URL=off");
        }
        if (!semanticClient.get().ping()) {
            throw new IllegalStateException("语义侧车无响应，请先启动 semantic-service");
        }

        List<MemoryEvent> targets = list().stream()
                .filter(record -> force || !record.hasEmbedding())
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .toList();
        if (targets.isEmpty()) {
            return 0;
        }

        System.out.printf("开始补向量，待处理 %d 条（batch=%d）...%n", targets.size(), EMBED_BATCH_SIZE);
        Map<String, MemoryEvent> allById = repository.load().stream()
                .collect(Collectors.toMap(MemoryEvent::id, record -> record, (left, right) -> left));

        int updated = 0;
        for (int offset = 0; offset < targets.size(); offset += EMBED_BATCH_SIZE) {
            int end = Math.min(offset + EMBED_BATCH_SIZE, targets.size());
            List<MemoryEvent> batch = targets.subList(offset, end);

            List<String> texts = batch.stream().map(MemoryEvent::content).toList();
            List<String> ids = batch.stream().map(MemoryEvent::id).toList();
            List<EmbedItem> vectors = semanticClient.get().embed(texts, ids).orElse(List.of());
            Map<String, List<Double>> vectorById = vectors.stream()
                    .collect(Collectors.toMap(EmbedItem::id, EmbedItem::embedding, (left, right) -> left));

            for (MemoryEvent record : batch) {
                List<Double> embedding = vectorById.get(record.id());
                if (embedding == null || embedding.isEmpty()) {
                    continue;
                }
                allById.put(record.id(), copyWithEmbedding(record, embedding));
                updated++;
            }

            if (end == targets.size() || end % 320 == 0) {
                System.out.printf("  补向量进度 %d / %d%n", end, targets.size());
            }
        }

        repository.rewrite(new ArrayList<>(allById.values()));
        System.out.printf("补向量完成，更新 %d 条。%n", updated);
        return updated;
    }

    @Override
    public boolean deleteByPrefix(String idPrefix) throws IOException {
        Optional<MemoryEvent> target = findByPrefix(idPrefix);
        if (target.isEmpty()) {
            return false;
        }

        MemoryEvent deleted = target.get();
        List<MemoryEvent> updated = repository.load().stream()
                .map(record -> record.id().equals(deleted.id())
                        ? copyDeleted(record)
                        : record)
                .toList();
        repository.rewrite(updated);
        return true;
    }

    @Override
    public List<MemoryEvent> list() throws IOException {
        return repository.load().stream()
                .filter(record -> !record.deleted())
                .toList();
    }

    @Override
    public Optional<MemoryEvent> findByPrefix(String idPrefix) throws IOException {
        return list().stream()
                .filter(record -> record.id().startsWith(idPrefix))
                .findFirst();
    }

    @Override
    public MemoryStats stats() throws IOException {
        List<MemoryEvent> all = repository.load();
        long active = all.stream().filter(record -> !record.deleted()).count();
        long withEmbedding = all.stream().filter(record -> !record.deleted() && record.hasEmbedding()).count();
        List<MemoryNode> nodes = graphRepository.loadNodes();
        long traces = nodes.stream().filter(node -> node.layer() == MemoryLayer.TRACE).count();
        long concepts = nodes.stream().filter(node -> node.layer() == MemoryLayer.CONCEPT).count();
        long themes = nodes.stream().filter(node -> node.layer() == MemoryLayer.THEME).count();
        long schemas = nodes.stream().filter(node -> node.layer() == MemoryLayer.SCHEMA).count();
        boolean semanticUp = semanticClient.map(SemanticServiceClient::ping).orElse(false);
        AssociativeMemoryStats associativeStats = associativeMemory.stats();

        return new MemoryStats(
                all.size(),
                active,
                all.size() - active,
                withEmbedding,
                nodes.size(),
                graphRepository.loadLinks().size(),
                traces,
                concepts,
                themes,
                schemas,
                !keywordIndex.isEmpty(),
                keywordIndex.termCount(),
                semanticUp,
                associativeStats.units(),
                associativeStats.synapses(),
                associativeStats.engrams()
        );
    }

    @Override
    public List<SearchResult> search(String query, int limit) throws IOException {
        Optional<SemanticUnderstanding> understanding = semanticClient.flatMap(client -> client.understand(query, limit));
        List<MemoryEvent> records = list();
        int candidateLimit = Math.max(limit * 2, limit);

        Set<String> candidateIds = new LinkedHashSet<>();
        if (!keywordIndex.isEmpty()) {
            candidateIds.addAll(keywordIndex.candidateIds(query, SEARCH_CANDIDATE_CAP));
        }

        List<SearchResult> graphHits = List.of();
        if (recallEngine.hasGraph()) {
            graphHits = recallEngine.recall(query, records, candidateLimit, understanding);
            for (SearchResult hit : graphHits) {
                candidateIds.add(hit.record().id());
            }
        }

        List<MemoryEvent> scoped = narrowRecords(records, candidateIds);
        List<SearchResult> keywordHits = searchEngine.search(query, scoped, candidateLimit, understanding);

        if (graphHits.isEmpty()) {
            return keywordHits.stream().limit(limit).toList();
        }
        return SearchMerger.merge(keywordHits, graphHits, limit);
    }

    @Override
    public List<AssociativeRecallResult> recall(String query, int limit) throws IOException {
        List<MemoryEvent> existing = list();
        List<AssociativeRecallResult> results = associativeMemory.recall(query, existing, limit);
        if (!results.isEmpty()) {
            recordRecallEvent(query, results.getFirst(), existing);
        }
        return results;
    }

    private void recordRecallEvent(
            String query,
            AssociativeRecallResult recalled,
            List<MemoryEvent> existing
    ) throws IOException {
        Instant now = Instant.now();
        MemoryEvent source = recalled.record();
        MemoryEvent event = new MemoryEvent(
                idGenerator.nextIdString(),
                source.topicKey(),
                EventType.RECALL,
                "回忆：" + TitleGenerator.title(query),
                "使用线索「" + query + "」回忆到「" + source.title() + "」",
                source.tags(),
                source.entities(),
                source.schemaLabel(),
                source.themeLabel(),
                source.conceptLabel(),
                List.of(),
                TemporalRange.point(now, TemporalPrecision.EXACT, "回忆发生时", 1.0),
                now,
                "recall",
                Math.max(0.0, Math.min(1.0, recalled.score())),
                false
        );
        repository.append(event);
        timelineIndexer.index(event, existing);
        indexer.index(event);
    }

    @Override
    public List<TimelineEntry> timeline(String query) throws IOException {
        String normalized = TextNormalizer.normalize(query);
        List<MemoryEvent> events = list();
        Set<String> matchedTopics = events.stream()
                .filter(event -> event.topicKey().contains(normalized)
                        || normalized.contains(event.topicKey())
                        || TextNormalizer.normalize(event.content()).contains(normalized))
                .map(MemoryEvent::topicKey)
                .filter(topic -> !topic.isBlank())
                .collect(Collectors.toSet());
        List<MemoryEvent> matched = events.stream()
                .filter(event -> matchedTopics.contains(event.topicKey()))
                .sorted(Comparator.comparing((MemoryEvent event) -> event.occurredTime().start())
                        .thenComparing(MemoryEvent::learnedAt))
                .toList();
        Set<String> matchedIds = matched.stream().map(MemoryEvent::id).collect(Collectors.toSet());
        Map<String, MemoryEventRelation> relationByTarget = timelineIndexer.relations().stream()
                .filter(relation -> matchedIds.contains(relation.fromEventId()) && matchedIds.contains(relation.toEventId()))
                .collect(Collectors.toMap(MemoryEventRelation::toEventId, relation -> relation, (left, right) -> right));
        return matched.stream()
                .map(event -> new TimelineEntry(
                        event,
                        Optional.ofNullable(relationByTarget.get(event.id())).map(MemoryEventRelation::type).orElse(null)
                ))
                .toList();
    }

    private List<MemoryEvent> narrowRecords(List<MemoryEvent> records, Set<String> candidateIds) {
        if (!candidateIds.isEmpty()) {
            return records.stream()
                    .filter(record -> candidateIds.contains(record.id()))
                    .toList();
        }
        return records.stream()
                .sorted(Comparator.comparing(MemoryEvent::learnedAt).reversed())
                .limit(SEARCH_RECENT_FALLBACK)
                .toList();
    }

    private void refreshKeywordIndex() throws IOException {
        keywordIndex = KeywordIndex.build(list());
        keywordIndex.save(keywordIndexFile);
    }

    private MemoryEvent createSeedRecord(String content) {
        Instant now = Instant.now();
        MemoryEvent draft = new MemoryEvent(
                idGenerator.nextIdString(),
                "",
                com.jinjin.memory.domain.EventType.OBSERVATION,
                TitleGenerator.title(content),
                content,
                fallbackTagger.tags(content),
                fallbackTagger.entities(content),
                "",
                "",
                "",
                List.of(),
                temporalParser.parse(content, now),
                now,
                "seed",
                0.8,
                false
        );
        return draft.withTopicKey(topicKeyResolver.resolve(draft));
    }

    private MemoryEvent copyWithEmbedding(MemoryEvent record, List<Double> embedding) {
        return new MemoryEvent(
                record.id(),
                record.topicKey(),
                record.eventType(),
                record.title(),
                record.content(),
                record.tags(),
                record.entities(),
                record.schemaLabel(),
                record.themeLabel(),
                record.conceptLabel(),
                embedding,
                record.occurredTime(),
                record.learnedAt(),
                record.source(),
                record.confidence(),
                record.deleted()
        );
    }

    private MemoryEvent copyDeleted(MemoryEvent record) {
        return new MemoryEvent(
                record.id(),
                record.topicKey(),
                record.eventType(),
                record.title(),
                record.content(),
                record.tags(),
                record.entities(),
                record.schemaLabel(),
                record.themeLabel(),
                record.conceptLabel(),
                record.embedding(),
                record.occurredTime(),
                record.learnedAt(),
                record.source(),
                record.confidence(),
                true
        );
    }
}
