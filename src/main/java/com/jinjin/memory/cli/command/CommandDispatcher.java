package com.jinjin.memory.cli.command;

public interface CommandDispatcher {
    CommandResult dispatch(ParsedCommand command);
}
