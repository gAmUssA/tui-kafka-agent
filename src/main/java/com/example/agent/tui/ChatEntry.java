package com.example.agent.tui;

import java.time.Instant;

/**
 * A single entry in the chat history.
 */
public record ChatEntry(Role role, String content, Instant timestamp) {

    public enum Role {
        USER, AGENT, TOOL, ERROR
    }

    public static ChatEntry user(String content) {
        return new ChatEntry(Role.USER, content, Instant.now());
    }

    public static ChatEntry agent(String content) {
        return new ChatEntry(Role.AGENT, content, Instant.now());
    }

    public static ChatEntry tool(String content) {
        return new ChatEntry(Role.TOOL, content, Instant.now());
    }

    public static ChatEntry error(String content) {
        return new ChatEntry(Role.ERROR, content, Instant.now());
    }
}
