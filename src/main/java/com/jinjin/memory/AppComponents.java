package com.jinjin.memory;

import com.jinjin.memory.cli.TerminalRenderer;
import com.jinjin.memory.cli.command.DefaultCommandDispatcher;
import com.jinjin.memory.cli.command.DefaultCommandParser;
import com.jinjin.memory.cli.command.DefaultCommandValidator;

record AppComponents(
        DefaultCommandParser parser,
        DefaultCommandValidator validator,
        DefaultCommandDispatcher dispatcher,
        TerminalRenderer renderer
) {
}
