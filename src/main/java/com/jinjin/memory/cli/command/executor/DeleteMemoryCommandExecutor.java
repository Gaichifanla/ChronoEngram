package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public class DeleteMemoryCommandExecutor implements CommandExecutor {
    private final MemoryCommandService service;

    public DeleteMemoryCommandExecutor(MemoryCommandService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.DELETE;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        boolean deleted = service.deleteByPrefix(command.argument());
        return CommandResult.success(deleted ? "已删除。" : "没有找到这条记忆。");
    }
}
