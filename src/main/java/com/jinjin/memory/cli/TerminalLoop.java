package com.jinjin.memory.cli;

import com.jinjin.memory.cli.command.CommandDispatcher;
import com.jinjin.memory.cli.command.CommandParser;
import com.jinjin.memory.cli.command.CommandResult;
import com.jinjin.memory.cli.command.CommandValidator;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.cli.command.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TerminalLoop {
    private final CommandParser parser;
    private final CommandValidator validator;
    private final CommandDispatcher dispatcher;
    private final TerminalRenderer renderer;

    public TerminalLoop(CommandParser parser, CommandValidator validator, CommandDispatcher dispatcher, TerminalRenderer renderer) {
        this.parser = parser;
        this.validator = validator;
        this.dispatcher = dispatcher;
        this.renderer = renderer;
    }

    public void run() {
        System.out.println("记忆数据库 MVP");
        System.out.println("输入 help 查看命令，输入 exit 退出。");

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("\nmemory> ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine();
            ParsedCommand command = parser.parse(line);
            ValidationResult validation = validator.validate(command);
            CommandResult result = validation.valid()
                    ? dispatcher.dispatch(command)
                    : validation.toCommandResult();
            if (result.shouldExit()) {
                break;
            }
            renderer.render(result);
        }
    }
}
