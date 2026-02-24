package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Signals that the AI streaming response has finished.
 */
public record StreamCompleteMessage(String fullResponse) implements Message {
}
