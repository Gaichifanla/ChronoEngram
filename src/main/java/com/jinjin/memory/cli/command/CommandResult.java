package com.jinjin.memory.cli.command;

public record CommandResult(
        boolean success,
        String message,
        Object payload,
        boolean shouldExit
) {
    public static CommandResult success(String message) {
        return new CommandResult(true, message, null, false);
    }

    public static CommandResult success(String message, Object payload) {
        return new CommandResult(true, message, payload, false);
    }

    public static CommandResult failure(String message) {
        return new CommandResult(false, message, null, false);
    }

    public static CommandResult exit() {
        return new CommandResult(true, "", null, true);
    }
}
