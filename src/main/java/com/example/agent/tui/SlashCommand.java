package com.example.agent.tui;

import java.util.List;

/**
 * Parsed slash command with name and arguments.
 */
public record SlashCommand(String name, List<String> args) {
}
