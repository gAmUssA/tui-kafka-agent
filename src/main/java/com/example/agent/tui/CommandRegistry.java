package com.example.agent.tui;

import com.example.agent.config.AppConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for all slash commands.
 * Used by both the suggestion popup and /help display.
 *
 * Call {@link #init(AppConfig)} once at startup to register
 * config-driven demo commands (/setup, /reset).
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

    private static final List<CommandInfo> BASE_COMMANDS = List.of(
        new CommandInfo("help", "", "Show this help message"),
        new CommandInfo("clear", "", "Clear chat history"),
        new CommandInfo("tools", "", "List available tools"),
        new CommandInfo("model", "[<provider>:]<name>", "Switch model (sonnet, haiku, opus, ollama:qwen2.5:7b, ...)"),
        new CommandInfo("thinking", "", "Toggle extended thinking mode"),
        new CommandInfo("mcp", "[name] <url> | disconnect [name]", "Manage MCP servers"),
        new CommandInfo("usage", "[reset]", "Show token usage and cost; reset clears totals"),
        new CommandInfo("topics", "", "List Kafka topics (via AI)"),
        new CommandInfo("sql", "<query>", "Run Flink SQL (via AI)"),
        new CommandInfo("quit", "", "Exit the application")
    );

    public static volatile List<CommandInfo> ALL = BASE_COMMANDS;

    private CommandRegistry() {
    }

    /**
     * Initialize config-driven demo commands. Call once at startup,
     * before the TUI loop begins.
     */
    public static void init(AppConfig config) {
        String setupPrompt = config.getDemoSetupPrompt();
        String resetPrompt = config.getDemoResetPrompt();
        if (setupPrompt == null && resetPrompt == null) {
            return; // no demo commands to add
        }
        String appName = config.getAppName();
        var commands = new ArrayList<>(BASE_COMMANDS);
        if (setupPrompt != null) {
            commands.add(new CommandInfo("setup", "", "Set up " + appName + " demo environment"));
        }
        if (resetPrompt != null) {
            commands.add(new CommandInfo("reset", "", "Reset " + appName + " demo environment"));
        }
        ALL = List.copyOf(commands);
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
