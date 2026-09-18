package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public class ReindexGraphCommandExecutor implements CommandExecutor {
    private final MemoryCommandService service;

    public ReindexGraphCommandExecutor(MemoryCommandService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.REINDEX;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        int indexed = service.reindexGraph();
        return CommandResult.success("已重建分层记忆索引：" + indexed + " 条原始记忆");
    }
}
