package com.jinjin.memory.cli.command;

import java.util.Locale;

public class DefaultCommandParser implements CommandParser {
    @Override
    public ParsedCommand parse(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isBlank()) {
            return new ParsedCommand(CommandType.UNKNOWN, "", "");
        }

        int splitAt = raw.indexOf(' ');
        String name = splitAt < 0 ? raw : raw.substring(0, splitAt);
        String argument = splitAt < 0 ? "" : raw.substring(splitAt + 1).trim();

        return new ParsedCommand(toType(name), raw, argument);
    }

    private CommandType toType(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "add" -> CommandType.ADD;
            case "search", "s" -> CommandType.SEARCH;
            case "recall", "r" -> CommandType.RECALL;
            case "timeline", "time" -> CommandType.TIMELINE;
            case "list" -> CommandType.LIST;
            case "show" -> CommandType.SHOW;
            case "delete", "del" -> CommandType.DELETE;
            case "seed" -> CommandType.SEED;
            case "reindex" -> CommandType.REINDEX;
            case "relearn", "rebuild-associative" -> CommandType.REBUILD_ASSOCIATIVE;
            case "embed" -> CommandType.EMBED;
            case "stats" -> CommandType.STATS;
            case "help", "-h", "--help" -> CommandType.HELP;
            case "exit", "quit" -> CommandType.EXIT;
            default -> CommandType.UNKNOWN;
        };
    }
}
