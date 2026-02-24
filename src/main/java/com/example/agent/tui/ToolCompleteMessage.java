package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Sent when a tool finishes executing. Carries the tool name and result text.
 */
public record ToolCompleteMessage(String toolName, String result) implements Message {
}
