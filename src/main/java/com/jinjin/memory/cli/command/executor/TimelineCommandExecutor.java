package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.TimelineEntry;

import java.util.List;

public class TimelineCommandExecutor implements CommandExecutor {
    private final MemoryQueryService service;

    public TimelineCommandExecutor(MemoryQueryService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.TIMELINE;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        List<TimelineEntry> entries = service.timeline(command.argument());
        String message = entries.isEmpty() ? "没有找到相关时间轴。" : "找到 " + entries.size() + " 个时间轴事件：";
        return CommandResult.success(message, entries);
    }
}
