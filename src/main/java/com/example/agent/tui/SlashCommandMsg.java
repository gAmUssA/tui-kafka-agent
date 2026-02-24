package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Message dispatched when the user enters a slash command.
 */
public record SlashCommandMsg(SlashCommand command) implements Message {
}
