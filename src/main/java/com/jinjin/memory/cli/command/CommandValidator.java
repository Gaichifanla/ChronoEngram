package com.jinjin.memory.cli.command;

public interface CommandValidator {
    ValidationResult validate(ParsedCommand command);
}
