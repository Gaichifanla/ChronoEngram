package com.jinjin.memory.application;

import com.jinjin.memory.domain.MemoryLayer;
import com.jinjin.memory.domain.MemoryLink;
import com.jinjin.memory.domain.MemoryLinkType;
import com.jinjin.memory.domain.MemoryNode;
import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.infrastructure.persistence.MemoryGraphRepository;
import com.jinjin.memory.infrastructure.util.SnowflakeIdGenerator;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryIndexer {
    private static final List<String> DEFAULT_SCHEMAS = List.of("学习", "工作", "生活", "健康", "财务", "人际");
    private static final Pattern ABOUT_PATTERN = Pattern.compile("关于([^，。,.]{2,16})的");

    private final MemoryGraphRepository repository;
    private final SnowflakeIdGenerator idGenerator;

    public MemoryIndexer(MemoryGraphRepository repository) {
        this.repository = repository;
        this.idGenerator = SnowflakeIdGenerator.defaultGenerator();
    }

    public void index(MemoryEvent record) throws IOException {
        index(List.of(record));
    }

    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    public void rebuild(List<MemoryEvent> records) throws IOException {
        rebuild(records, null);
    }

    public void rebuild(List<MemoryEvent> records, ProgressListener listener) throws IOException {
        repository.clear();
        int batchSize = 200;
        int total = records.size();
        for (int offset = 0; offset < total; offset += batchSize) {
            int end = Math.min(offset + batchSize, total);
            index(records.subList(offset, end));
            if (listener != null) {
                listener.onProgress(end, total);
            }
        }
    }

    public void index(List<MemoryEvent> records) throws IOException {
        Map<String, MemoryNode> nodeCache = new LinkedHashMap<>();
        for (MemoryNode node : repository.loadNodes()) {
            nodeCache.put(key(node.layer(), node.label()), node);
        }

        List<MemoryNode> newNodes = new ArrayList<>();
        List<MemoryLink> newLinks = new ArrayList<>();
        for (MemoryEvent record : records) {
            if (record.deleted()) {
                continue;
            }

            Instant now = Instant.now();
            MemoryNode trace = traceNode(record, now);
            newNodes.add(trace);

            MemoryNode schema = ensureNode(nodeCache, newNodes, MemoryLayer.SCHEMA, schemaLabel(record), now);
            MemoryNode theme = ensureNode(nodeCache, newNodes, MemoryLayer.THEME, themeLabel(record), now);
            MemoryNode concept = ensureNode(nodeCache, newNodes, MemoryLayer.CONCEPT, conceptLabel(record), now);

            newLinks.add(link(theme.id(), schema.id(), MemoryLinkType.PART_OF, 0.9, now));
            newLinks.add(link(concept.id(), theme.id(), MemoryLinkType.PART_OF, 0.9, now));
            newLinks.add(link(trace.id(), concept.id(), MemoryLinkType.DERIVED_FROM, 1.0, now));

            for (String entity : record.entities()) {
                if (!entity.equals(concept.label())) {
                    MemoryNode entityConcept = ensureNode(nodeCache, newNodes, MemoryLayer.CONCEPT, entity, now);
                    newLinks.add(link(concept.id(), entityConcept.id(), MemoryLinkType.RELATED, 0.55, now));
                    newLinks.add(link(trace.id(), entityConcept.id(), MemoryLinkType.DERIVED_FROM, 0.75, now));
                }
            }
        }

        repository.appendNodes(newNodes);
        repository.appendLinks(newLinks);
    }

    private MemoryNode ensureNode(
            Map<String, MemoryNode> nodeCache,
            List<MemoryNode> newNodes,
            MemoryLayer layer,
            String label,
            Instant now
    ) {
        String normalizedLabel = label == null || label.isBlank() ? "未分类" : label.trim();
        String key = key(layer, normalizedLabel);
        MemoryNode existing = nodeCache.get(key);
        if (existing != null) {
            return existing;
        }

        MemoryNode node = new MemoryNode(
                idGenerator.nextIdString(),
                layer,
                normalizedLabel,
                normalizedLabel,
                "",
                List.of(normalizedLabel),
                List.of(),
                0.8,
                0,
                now,
                now,
                now,
                1
        );
        nodeCache.put(key, node);
        newNodes.add(node);
        return node;
    }

    private MemoryNode traceNode(MemoryEvent record, Instant now) {
        return new MemoryNode(
                record.id(),
                MemoryLayer.TRACE,
                record.title(),
                record.title(),
                record.content(),
                keywords(record),
                record.entities(),
                1.0,
                0,
                record.learnedAt(),
                now,
                now,
                1
        );
    }

    private List<String> keywords(MemoryEvent record) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(record.tags());
        values.addAll(record.entities());
        values.add(record.title());
        return List.copyOf(values);
    }

    private String schemaLabel(MemoryEvent record) {
        if (record.schemaLabel() != null && !record.schemaLabel().isBlank()) {
            return record.schemaLabel();
        }
        String content = record.content();
        if (containsAny(content, "老师", "古诗", "学习", "课程", "课堂", "背诵", "读书")) {
            return "学习";
        }
        if (containsAny(content, "部署", "服务器", "数据库", "接口", "合同", "客户", "会议", "项目")) {
            return "工作";
        }
        if (containsAny(content, "运动", "睡眠", "感冒", "用药", "眼睛", "健康")) {
            return "健康";
        }
        if (containsAny(content, "记账", "付款", "报价", "预算", "财务")) {
            return "财务";
        }
        if (containsAny(content, "沟通", "朋友", "同事", "家人")) {
            return "人际";
        }
        return DEFAULT_SCHEMAS.get(2);
    }

    private String themeLabel(MemoryEvent record) {
        if (record.themeLabel() != null && !record.themeLabel().isBlank()) {
            return record.themeLabel();
        }
        if (!record.tags().isEmpty() && !"未分类".equals(record.tags().getFirst())) {
            return record.tags().getFirst();
        }
        String content = record.content();
        if (containsAny(content, "古诗", "登鹳雀楼", "王之涣")) {
            return "古诗";
        }
        if (containsAny(content, "生活常识", "饮水", "睡眠", "运动", "早餐")) {
            return "生活常识";
        }
        return "未分类";
    }

    private String conceptLabel(MemoryEvent record) {
        if (record.conceptLabel() != null && !record.conceptLabel().isBlank()) {
            return record.conceptLabel();
        }
        if (!record.entities().isEmpty()) {
            return record.entities().getFirst();
        }
        String content = record.content();
        if (content.contains("登鹳雀楼")) {
            return "登鹳雀楼";
        }
        Matcher aboutMatcher = ABOUT_PATTERN.matcher(content);
        if (aboutMatcher.find()) {
            return aboutMatcher.group(1).trim();
        }
        String title = record.title();
        int marker = title.indexOf('：');
        if (marker >= 0 && marker + 1 < title.length()) {
            return title.substring(marker + 1).replace("...", "").trim();
        }
        return title.replace("...", "").trim();
    }

    private boolean containsAny(String content, String... words) {
        for (String word : words) {
            if (content.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private MemoryLink link(String fromNodeId, String toNodeId, MemoryLinkType type, double weight, Instant now) {
        return new MemoryLink(
                idGenerator.nextIdString(),
                fromNodeId,
                toNodeId,
                type,
                weight,
                0.85,
                now,
                now
        );
    }

    private String key(MemoryLayer layer, String label) {
        return layer.name() + ":" + label;
    }
}
