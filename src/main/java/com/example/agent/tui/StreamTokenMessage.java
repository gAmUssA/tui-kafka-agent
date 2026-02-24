package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Delivers a single token from the AI streaming response into the TUI event loop.
 */
public record StreamTokenMessage(String token) implements Message {
}
