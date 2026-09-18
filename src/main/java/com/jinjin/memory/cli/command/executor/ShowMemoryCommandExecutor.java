package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.MemoryEvent;

import java.util.Optional;

public class ShowMemoryCommandExecutor implements CommandExecutor {
    private final MemoryQueryService service;

    public ShowMemoryCommandExecutor(MemoryQueryService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.SHOW;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        Optional<MemoryEvent> record = service.findByPrefix(command.argument());
        return record
                .map(memory -> CommandResult.success("", memory))
                .orElseGet(() -> CommandResult.success("没有找到这条记忆。"));
    }
}
