package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Delivers an error from AI streaming or tool execution into the TUI event loop.
 */
public record ErrorMessage(String error) implements Message {

}
