package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public class EmbedCommandExecutor implements CommandExecutor {
    private final MemoryCommandService service;

    public EmbedCommandExecutor(MemoryCommandService service) {
        this.service = service;
    }

    @Override
    public CommandType supports() {
        return CommandType.EMBED;
    }

    @Override
    public CommandResult execute(ParsedCommand command) throws Exception {
        int limit = 0;
        boolean force = false;
        for (String token : command.argument().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if ("--force".equals(token)) {
                force = true;
            } else {
                limit = Integer.parseInt(token);
            }
        }
        int updated = service.backfillEmbeddings(limit, force);
        return CommandResult.success("已更新 " + updated + " 条记忆的向量。");
    }
}
