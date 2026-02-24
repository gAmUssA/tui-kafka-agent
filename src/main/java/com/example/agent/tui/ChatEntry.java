package com.example.agent.tui;

import java.time.Instant;

/**
 * A single entry in the chat history.
 */
public record ChatEntry(Role role, String content, String toolName, Instant timestamp) {

    public enum Role {
        USER, AGENT, TOOL, ERROR
    }

    public static ChatEntry user(String content) {
        return new ChatEntry(Role.USER, content, null, Instant.now());
    }

    public static ChatEntry agent(String content) {
        return new ChatEntry(Role.AGENT, content, null, Instant.now());
    }

    public static ChatEntry tool(String content) {
        return new ChatEntry(Role.TOOL, content, null, Instant.now());
    }

    public static ChatEntry tool(String toolName, String content) {
        return new ChatEntry(Role.TOOL, content, toolName, Instant.now());
    }

    public static ChatEntry error(String content) {
        return new ChatEntry(Role.ERROR, content, null, Instant.now());
    }
}
