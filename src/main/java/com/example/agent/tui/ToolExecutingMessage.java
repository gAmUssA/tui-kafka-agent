package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Sent when a tool begins executing. Triggers spinner display.
 */
public record ToolExecutingMessage(String toolName) implements Message {
}
