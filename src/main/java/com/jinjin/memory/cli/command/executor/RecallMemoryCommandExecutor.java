package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.domain.AssociativeRecallResult;

import java.util.List;

public class RecallMemoryCommandExecutor implements CommandExecutor {
    private final MemoryQueryService service;

    public RecallMemoryCommandExecutor(MemoryQueryService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.RECALL;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        List<AssociativeRecallResult> results = service.recall(command.argument(), 8);
        if (results.isEmpty()) {
            return CommandResult.success("没有形成足够强的联想。请先运行 relearn，或增加相关记忆。", results);
        }
        return CommandResult.success("联想到 " + results.size() + " 条记忆：", results);
    }
}
