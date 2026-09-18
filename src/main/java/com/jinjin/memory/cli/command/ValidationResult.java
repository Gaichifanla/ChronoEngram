package com.jinjin.memory.cli.command;

public record ValidationResult(boolean valid, String message) {
    public static ValidationResult ok() {
        return new ValidationResult(true, "");
    }

    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }

    public CommandResult toCommandResult() {
        return CommandResult.failure(message);
    }
}
