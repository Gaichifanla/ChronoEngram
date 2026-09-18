package com.jinjin.memory.cli.command.executor;

import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandType;
import com.jinjin.memory.cli.command.ParsedCommand;

public interface CommandExecutor {
    CommandType supports();

    CommandResult execute(ParsedCommand command) throws Exception;
}
