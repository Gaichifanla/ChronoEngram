package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.MemoryStats;

public class StatsCommandExecutor implements CommandExecutor {
    private final MemoryQueryService service;

    public StatsCommandExecutor(MemoryQueryService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.STATS;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        MemoryStats stats = service.stats();
        String message = """
                记忆统计：
                  总记录：%d（活跃 %d，已删 %d）
                  含向量：%d
                  分层图：节点 %d，边 %d（Trace %d / Concept %d / Theme %d / Schema %d）
                  关键词索引：%s（词条 %d）
                  联想网络：单元 %d，突触 %d，Engram %d
                  语义侧车：%s
                """.formatted(
                stats.totalRecords(),
                stats.activeRecords(),
                stats.deletedRecords(),
                stats.withEmbedding(),
                stats.graphNodes(),
                stats.graphLinks(),
                stats.graphTraces(),
                stats.graphConcepts(),
                stats.graphThemes(),
                stats.graphSchemas(),
                stats.keywordIndexReady() ? "就绪" : "未构建（请执行 reindex）",
                stats.keywordIndexTerms(),
                stats.associativeUnits(),
                stats.associativeSynapses(),
                stats.associativeEngrams(),
                stats.semanticServiceUp() ? "在线" : "离线/禁用"
        );
        return CommandResult.success(message.strip(), stats);
    }
}
