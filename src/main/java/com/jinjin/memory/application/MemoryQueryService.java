package com.jinjin.memory.application;

import com.jinjin.memory.domain.MemoryEvent;
import com.jinjin.memory.domain.MemoryStats;
import com.jinjin.memory.domain.SearchResult;
import com.jinjin.memory.domain.AssociativeRecallResult;
import com.jinjin.memory.domain.TimelineEntry;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface MemoryQueryService {
    List<MemoryEvent> list() throws IOException;

    Optional<MemoryEvent> findByPrefix(String idPrefix) throws IOException;

    List<SearchResult> search(String query, int limit) throws IOException;

    List<AssociativeRecallResult> recall(String query, int limit) throws IOException;

    List<TimelineEntry> timeline(String query) throws IOException;

    MemoryStats stats() throws IOException;
}
