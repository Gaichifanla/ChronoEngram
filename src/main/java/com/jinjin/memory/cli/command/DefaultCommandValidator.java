package com.jinjin.memory.cli.command;

public class DefaultCommandValidator implements CommandValidator {
    @Override
    public ValidationResult validate(ParsedCommand command) {
        return switch (command.type()) {
            case ADD -> requireArgument(command, "用法：add 你的记忆内容");
            case SEARCH -> requireArgument(command, "用法：search 你的问题、陈述句或关键词");
            case RECALL -> requireArgument(command, "用法：recall 你的回忆线索");
            case TIMELINE -> requireArgument(command, "用法：timeline 主题或事件关键词");
            case SHOW -> requireArgument(command, "用法：show 记忆ID前缀");
            case DELETE -> requireArgument(command, "用法：delete 记忆ID前缀");
            case SEED -> validateSeed(command);
            case EMBED -> validateEmbed(command);
            case UNKNOWN -> ValidationResult.invalid("未知命令，输入 help 查看可用命令。");
            case LIST, REINDEX, REBUILD_ASSOCIATIVE, STATS, HELP, EXIT -> ValidationResult.ok();
        };
    }

    private ValidationResult requireArgument(ParsedCommand command, String message) {
        if (command.argument().isBlank()) {
            return ValidationResult.invalid(message);
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateEmbed(ParsedCommand command) {
        if (command.argument().isBlank()) {
            return ValidationResult.ok();
        }
        try {
            for (String token : command.argument().split("\\s+")) {
                if (token.isBlank() || "--force".equals(token)) {
                    continue;
                }
                int count = Integer.parseInt(token);
                if (count <= 0) {
                    return ValidationResult.invalid("embed 数量必须大于 0。");
                }
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("用法：embed [数量] [--force]，例如 embed 1000");
        }
    }

    private ValidationResult validateSeed(ParsedCommand command) {
        if (command.argument().isBlank()) {
            return ValidationResult.ok();
        }
        try {
            int count = Integer.parseInt(command.argument());
            if (count <= 0) {
                return ValidationResult.invalid("seed 数量必须大于 0。");
            }
            if (count > 1_000_000) {
                return ValidationResult.invalid("seed 数量不能超过 1000000。");
            }
            return ValidationResult.ok();
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("用法：seed [数量]，例如 seed 100000");
        }
    }
}
