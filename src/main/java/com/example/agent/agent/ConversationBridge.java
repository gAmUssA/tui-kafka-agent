package com.example.agent.agent;

/**
 * One conversational turn in, {@link AgentEvent}s out. The TUI talks to this and
 * nothing else, so the single-assistant and multi-agent implementations are
 * interchangeable at runtime ({@code /agentic on|off}).
 */
public interface ConversationBridge {

    /** Start processing a user message. Returns immediately; results arrive as events. */
    void sendMessage(String userInput);

    /** Short label for the header bar, e.g. {@code single} or {@code agentic}. */
    String modeLabel();

    /** Forget conversation state for this session (used by {@code /clear}). */
    default void reset() {
    }
}
