package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public class SeedBasicKnowledgeCommandExecutor implements CommandExecutor {
    private static final int DEFAULT_COUNT = 100_000;
    private final MemoryCommandService service;

    public SeedBasicKnowledgeCommandExecutor(MemoryCommandService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.SEED;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        int count = command.argument().isBlank() ? DEFAULT_COUNT : Integer.parseInt(command.argument());
        int inserted = service.seedBasicKnowledge(count);
        return CommandResult.success("已写入生活常识记忆：" + inserted + " 条");
    }
}
