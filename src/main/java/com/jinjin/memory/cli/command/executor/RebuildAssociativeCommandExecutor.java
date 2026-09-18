package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public class RebuildAssociativeCommandExecutor implements CommandExecutor {
    private final MemoryCommandService service;

    public RebuildAssociativeCommandExecutor(MemoryCommandService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.REBUILD_ASSOCIATIVE;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        int learned = service.rebuildAssociativeMemory();
        return CommandResult.success("已重建联想记忆网络：" + learned + " 条 Engram");
    }
}
