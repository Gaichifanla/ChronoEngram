package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryEventRelation;

import java.io.IOException;
import java.util.List;

public interface MemoryTimelineRepository {
    List<MemoryEventRelation> loadRelations() throws IOException;

    void append(MemoryEventRelation relation) throws IOException;

    void replaceAll(List<MemoryEventRelation> relations) throws IOException;
}
