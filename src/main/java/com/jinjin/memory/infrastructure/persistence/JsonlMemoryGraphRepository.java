package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryLayer;
import com.jinjin.memory.domain.MemoryLink;
import com.jinjin.memory.domain.MemoryLinkType;
import com.jinjin.memory.domain.MemoryNode;
import com.jinjin.memory.infrastructure.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JsonlMemoryGraphRepository implements MemoryGraphRepository {
    private final Path nodesFile;
    private final Path linksFile;

    public JsonlMemoryGraphRepository(Path graphDir) {
        this.nodesFile = graphDir.resolve("nodes.jsonl");
        this.linksFile = graphDir.resolve("links.jsonl");
    }

    @Override
    public Optional<MemoryNode> findNodeByLayerAndLabel(String layer, String label) throws IOException {
        return loadNodes().stream()
                .filter(node -> node.layer().name().equals(layer) && node.label().equals(label))
                .findFirst();
    }

    @Override
    public void appendNode(MemoryNode node) throws IOException {
        appendNodes(List.of(node));
    }

    @Override
    public void appendNodes(List<MemoryNode> nodes) throws IOException {
        appendLines(nodesFile, nodes.stream().map(this::toJsonLine).toList());
    }

    @Override
    public void appendLink(MemoryLink link) throws IOException {
        appendLinks(List.of(link));
    }

    @Override
    public void appendLinks(List<MemoryLink> links) throws IOException {
        appendLines(linksFile, links.stream().map(this::toJsonLine).toList());
    }

    @Override
    public List<MemoryNode> loadNodes() throws IOException {
        if (!Files.exists(nodesFile)) {
            return List.of();
        }
        List<MemoryNode> nodes = new ArrayList<>();
        for (String line : Files.readAllLines(nodesFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                nodes.add(nodeFromJson(line));
            }
        }
        return nodes;
    }

    @Override
    public void clear() throws IOException {
        Path parent = nodesFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(nodesFile, "", StandardCharsets.UTF_8);
        Files.writeString(linksFile, "", StandardCharsets.UTF_8);
    }

    @Override
    public List<MemoryLink> loadLinks() throws IOException {
        if (!Files.exists(linksFile)) {
            return List.of();
        }
        List<MemoryLink> links = new ArrayList<>();
        for (String line : Files.readAllLines(linksFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                links.add(linkFromJson(line));
            }
        }
        return links;
    }

    private void appendLines(Path file, List<String> lines) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append(System.lineSeparator());
        }
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private String toJsonLine(MemoryNode node) {
        return "{" +
                "\"id\":\"" + Json.escape(node.id()) + "\"," +
                "\"layer\":\"" + node.layer() + "\"," +
                "\"label\":\"" + Json.escape(node.label()) + "\"," +
                "\"summary\":\"" + Json.escape(node.summary()) + "\"," +
                "\"content\":\"" + Json.escape(node.content()) + "\"," +
                "\"keywords\":" + Json.writeArray(node.keywords()) + "," +
                "\"entities\":" + Json.writeArray(node.entities()) + "," +
                "\"confidence\":" + node.confidence() + "," +
                "\"importance\":" + node.importance() + "," +
                "\"createdAt\":\"" + node.createdAt() + "\"," +
                "\"updatedAt\":\"" + node.updatedAt() + "\"," +
                "\"lastActivatedAt\":\"" + node.lastActivatedAt() + "\"," +
                "\"activationCount\":" + node.activationCount() +
                "}";
    }

    private String toJsonLine(MemoryLink link) {
        return "{" +
                "\"id\":\"" + Json.escape(link.id()) + "\"," +
                "\"fromNodeId\":\"" + Json.escape(link.fromNodeId()) + "\"," +
                "\"toNodeId\":\"" + Json.escape(link.toNodeId()) + "\"," +
                "\"type\":\"" + link.type() + "\"," +
                "\"weight\":" + link.weight() + "," +
                "\"confidence\":" + link.confidence() + "," +
                "\"createdAt\":\"" + link.createdAt() + "\"," +
                "\"updatedAt\":\"" + link.updatedAt() + "\"" +
                "}";
    }

    private MemoryNode nodeFromJson(String json) {
        return new MemoryNode(
                Json.readString(json, "id"),
                MemoryLayer.valueOf(Json.readString(json, "layer")),
                Json.readString(json, "label"),
                Json.readString(json, "summary"),
                Json.readString(json, "content"),
                Json.readArray(json, "keywords"),
                Json.readArray(json, "entities"),
                Json.readDouble(json, "confidence", 1.0),
                Json.readInt(json, "importance", 0),
                Instant.parse(Json.readString(json, "createdAt")),
                Instant.parse(Json.readString(json, "updatedAt")),
                Instant.parse(Json.readString(json, "lastActivatedAt")),
                Json.readLong(json, "activationCount", 0)
        );
    }

    private MemoryLink linkFromJson(String json) {
        return new MemoryLink(
                Json.readString(json, "id"),
                Json.readString(json, "fromNodeId"),
                Json.readString(json, "toNodeId"),
                MemoryLinkType.valueOf(Json.readString(json, "type")),
                Json.readDouble(json, "weight", 1.0),
                Json.readDouble(json, "confidence", 1.0),
                Instant.parse(Json.readString(json, "createdAt")),
                Instant.parse(Json.readString(json, "updatedAt"))
        );
    }
}
