package com.example.agent.config;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single MCP server connection.
 *
 * @param type    transport type: "sse" or "stdio"
 * @param url     SSE endpoint URL (required for type=sse)
 * @param command command + args to spawn (required for type=stdio)
 * @param env     extra environment variables for the subprocess (stdio only)
 */
public record McpServerConfig(
        String type,
        String url,
        List<String> command,
        Map<String, String> env
) {
    public boolean isSse() {
        return "sse".equalsIgnoreCase(type);
    }

    public boolean isStdio() {
        return "stdio".equalsIgnoreCase(type);
    }
}
