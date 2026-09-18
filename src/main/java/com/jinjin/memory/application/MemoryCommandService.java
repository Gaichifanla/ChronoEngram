package com.jinjin.memory.application;

import com.jinjin.memory.domain.MemoryEvent;

import java.io.IOException;

public interface MemoryCommandService {
    MemoryEvent add(String content) throws IOException;

    int seedBasicKnowledge(int count) throws IOException;

    int reindexGraph() throws IOException;

    int rebuildAssociativeMemory() throws IOException;

    /** 为缺失 embedding 的记忆批量补向量；limit<=0 表示不限制条数。 */
    int backfillEmbeddings(int limit, boolean force) throws IOException;

    boolean deleteByPrefix(String idPrefix) throws IOException;
}
