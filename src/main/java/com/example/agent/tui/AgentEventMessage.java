package com.example.agent.tui;

import com.example.agent.agent.AgentEvent;
import com.williamcallahan.tui4j.compat.bubbletea.Message;

/**
 * Wraps an {@link AgentEvent} so it can travel through the tui4j event loop
 * via {@code Program.send(...)}. The only tui4j-specific piece of the seam.
 */
public record AgentEventMessage(AgentEvent event) implements Message {
}
