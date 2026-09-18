package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.MemoryEvent;

public class AddMemoryCommandExecutor implements CommandExecutor {
    private final MemoryCommandService service;

    public AddMemoryCommandExecutor(MemoryCommandService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.ADD;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        MemoryEvent record = service.add(command.argument());
        StringBuilder message = new StringBuilder()
                .append("已保存记忆：").append(record.shortId()).append(System.lineSeparator())
                .append("标题：").append(record.title()).append(System.lineSeparator())
                .append("标签：").append(String.join(", ", record.tags()));
        if (!record.entities().isEmpty()) {
            message.append(System.lineSeparator()).append("实体：").append(String.join(", ", record.entities()));
        }
        return CommandResult.success(message.toString());
    }
}
