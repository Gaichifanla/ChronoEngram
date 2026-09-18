package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.MemoryEvent;

import java.util.Comparator;
import java.util.List;

public class ListMemoryCommandExecutor implements CommandExecutor {
    private final MemoryQueryService service;

    public ListMemoryCommandExecutor(MemoryQueryService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.LIST;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        List<MemoryEvent> records = service.list().stream()
                .sorted(Comparator.comparing(MemoryEvent::learnedAt).reversed())
                .toList();
        if (records.isEmpty()) {
            return CommandResult.success("还没有记忆。", records);
        }
        return CommandResult.success("", records);
    }
}
