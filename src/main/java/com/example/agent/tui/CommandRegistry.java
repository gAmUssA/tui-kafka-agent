package com.example.agent.tui;

import java.util.List;

/**
 * Single source of truth for all slash commands.
 * Used by both the suggestion popup and /help display.
 */
public final class CommandRegistry {

    public record CommandInfo(String name, String args, String description) {
        public String displayName() {
            return args.isEmpty() ? "/" + name : "/" + name + " " + args;
        }

        public boolean hasArgs() {
            return !args.isEmpty();
        }
    }

    public static final List<CommandInfo> ALL = List.of(
        new CommandInfo("help", "", "Show this help message"),
        new CommandInfo("clear", "", "Clear chat history"),
        new CommandInfo("tools", "", "List available tools"),
        new CommandInfo("model", "<name>", "Switch model (sonnet, haiku, opus)"),
        new CommandInfo("thinking", "", "Toggle extended thinking mode"),
        new CommandInfo("mcp", "<url>", "Connect to MCP server"),
        new CommandInfo("topics", "", "List Kafka topics (via AI)"),
        new CommandInfo("sql", "<query>", "Run Flink SQL (via AI)"),
        new CommandInfo("reset-brewery", "", "Reset demo (delete brewery topics, schemas, & Flink jobs)")
    );

    private CommandRegistry() {
    }

    /**
     * Filter commands whose name starts with the given prefix.
     * @param prefix text after the '/' (e.g. "re" for "/re")
     */
    public static List<CommandInfo> filter(String prefix) {
        if (prefix.isEmpty()) {
            return ALL;
        }
        return ALL.stream()
            .filter(c -> c.name().startsWith(prefix))
            .toList();
    }
}
