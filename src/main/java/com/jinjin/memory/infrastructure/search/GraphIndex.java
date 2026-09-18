package com.jinjin.memory.infrastructure.search;

import com.jinjin.memory.domain.MemoryLayer;
import com.jinjin.memory.domain.MemoryLink;
import com.jinjin.memory.domain.MemoryLinkType;
import com.jinjin.memory.domain.MemoryNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 记忆图邻接索引，用于分层扩散召回。 */
final class GraphIndex {
    private final Map<String, MemoryNode> nodesById;
    private final Map<String, List<MemoryLink>> linksByTo;
    private final Map<String, List<MemoryLink>> linksByFrom;

    GraphIndex(List<MemoryNode> nodes, List<MemoryLink> links) {
        this.nodesById = new HashMap<>();
        for (MemoryNode node : nodes) {
            nodesById.put(node.id(), node);
        }
        this.linksByTo = new HashMap<>();
        this.linksByFrom = new HashMap<>();
        for (MemoryLink link : links) {
            linksByTo.computeIfAbsent(link.toNodeId(), ignored -> new ArrayList<>()).add(link);
            linksByFrom.computeIfAbsent(link.fromNodeId(), ignored -> new ArrayList<>()).add(link);
        }
    }

    Optional<MemoryNode> node(String id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    /** PART_OF：from 是子节点，to 是父节点。 */
    Optional<MemoryNode> parent(MemoryNode child) {
        for (MemoryLink link : linksByFrom.getOrDefault(child.id(), List.of())) {
            if (link.type() == MemoryLinkType.PART_OF) {
                return node(link.toNodeId());
            }
        }
        return Optional.empty();
    }

    List<MemoryNode> children(MemoryNode parent) {
        List<MemoryNode> children = new ArrayList<>();
        for (MemoryLink link : linksByTo.getOrDefault(parent.id(), List.of())) {
            if (link.type() == MemoryLinkType.PART_OF) {
                node(link.fromNodeId()).ifPresent(children::add);
            }
        }
        return children;
    }

    List<MemoryNode> tracesFor(MemoryNode conceptOrTrace) {
        List<MemoryNode> traces = new ArrayList<>();
        if (conceptOrTrace.layer() == MemoryLayer.TRACE) {
            traces.add(conceptOrTrace);
            return traces;
        }
        for (MemoryLink link : linksByTo.getOrDefault(conceptOrTrace.id(), List.of())) {
            if (link.type() == MemoryLinkType.DERIVED_FROM) {
                node(link.fromNodeId())
                        .filter(node -> node.layer() == MemoryLayer.TRACE)
                        .ifPresent(traces::add);
            }
        }
        return traces;
    }

    List<String> recallPath(MemoryNode trace) {
        List<String> path = new ArrayList<>();
        if (trace.layer() != MemoryLayer.TRACE) {
            path.add(trace.label());
            return path;
        }

        MemoryNode concept = null;
        for (MemoryLink link : linksByFrom.getOrDefault(trace.id(), List.of())) {
            if (link.type() == MemoryLinkType.DERIVED_FROM) {
                concept = nodesById.get(link.toNodeId());
                break;
            }
        }
        if (concept == null) {
            path.add(trace.label());
            return path;
        }

        Optional<MemoryNode> theme = parent(concept);
        Optional<MemoryNode> schema = theme.flatMap(this::parent);

        schema.ifPresent(node -> path.add(node.label()));
        theme.ifPresent(node -> path.add(node.label()));
        path.add(concept.label());
        path.add(trace.label());
        return path;
    }
}
