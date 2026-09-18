package com.jinjin.memory.cli.command;

import com.jinjin.memory.cli.command.executor.CommandExecutor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DefaultCommandDispatcher implements CommandDispatcher {
    private final Map<CommandType, CommandExecutor> executors = new EnumMap<>(CommandType.class);

    public DefaultCommandDispatcher(List<CommandExecutor> executors) {
        for (CommandExecutor executor : executors) {
            this.executors.put(executor.supports(), executor);
        }
    }

    @Override
    public CommandResult dispatch(ParsedCommand command) {
        if (command.type() == CommandType.EXIT) {
            return CommandResult.exit();
        }

        CommandExecutor executor = executors.get(command.type());
        if (executor == null) {
            return CommandResult.failure("未知命令，输入 help 查看可用命令。");
        }

        try {
            return executor.execute(command);
        } catch (Exception e) {
            return CommandResult.failure("执行失败：" + e.getMessage());
        }
    }
}
