package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.SearchResult;

import java.util.List;

public class SearchMemoryCommandExecutor implements CommandExecutor {
    private final MemoryQueryService service;

    public SearchMemoryCommandExecutor(MemoryQueryService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.SEARCH;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        List<SearchResult> results = service.search(command.argument(), 8);
        if (results.isEmpty()) {
            return CommandResult.success("没有找到相关记忆。", results);
        }
        return CommandResult.success("找到 " + results.size() + " 条相关记忆：", results);
    }
}
