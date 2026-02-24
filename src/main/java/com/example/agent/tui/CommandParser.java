package com.example.agent.tui;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Parses input strings into SlashCommand if they start with '/'.
 */
public final class CommandParser {

  private CommandParser() {
  }

  public static Optional<SlashCommand> parse(String input) {
    if (input == null || !input.startsWith("/")) {
      return Optional.empty();
    }
    String[] parts = input.substring(1).split("\\s+", -1);
    if (parts.length == 0 || parts[0].isEmpty()) {
      return Optional.empty();
    }
    String name = parts[0].toLowerCase();
    List<String> args = parts.length > 1
                        ? Arrays.asList(Arrays.copyOfRange(parts, 1, parts.length))
                        : List.of();
    return Optional.of(new SlashCommand(name, args));
  }
}
