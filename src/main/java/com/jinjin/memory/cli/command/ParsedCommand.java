package com.jinjin.memory.cli.command;

public record ParsedCommand(
        CommandType type,
        String rawInput,
        String argument
) {
}
