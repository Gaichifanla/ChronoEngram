package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryLink;
import com.jinjin.memory.domain.MemoryNode;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface MemoryGraphRepository {
    Optional<MemoryNode> findNodeByLayerAndLabel(String layer, String label) throws IOException;

    void appendNode(MemoryNode node) throws IOException;

    void appendNodes(List<MemoryNode> nodes) throws IOException;

    void appendLink(MemoryLink link) throws IOException;

    void appendLinks(List<MemoryLink> links) throws IOException;

    List<MemoryNode> loadNodes() throws IOException;

    List<MemoryLink> loadLinks() throws IOException;

    void clear() throws IOException;
}
