package com.jinjin.memory.infrastructure.persistence;

import com.jinjin.memory.domain.MemoryEvent;

import java.io.IOException;
import java.util.List;

public interface MemoryRepository {
    void append(MemoryEvent record) throws IOException;

    void appendAll(List<MemoryEvent> records) throws IOException;

    List<MemoryEvent> load() throws IOException;

    void rewrite(List<MemoryEvent> records) throws IOException;
}
