package com.example.agent.config;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single MCP server connection.
 *
 * @param type    transport type: {@code streamable-http} (default; aliases {@code http},
 *                and the legacy {@code sse}) or {@code stdio}
 * @param url     MCP endpoint URL (required for HTTP transports, typically ending in {@code /mcp})
 * @param command command + args to spawn (required for type=stdio)
 * @param env     extra environment variables for the subprocess (stdio only)
 */
public record McpServerConfig(
        String type,
        String url,
        List<String> command,
        Map<String, String> env
) {
    public static final String TYPE_STREAMABLE_HTTP = "streamable-http";
    public static final String TYPE_STDIO = "stdio";

    /**
     * True for any HTTP-based transport. LangChain4j 1.19+ only ships the Streamable HTTP
     * client, so {@code sse} is accepted as a legacy alias but the server must speak
     * Streamable HTTP (POST to the {@code /mcp} endpoint).
     */
    public boolean isHttp() {
        return type == null
                || TYPE_STREAMABLE_HTTP.equalsIgnoreCase(type)
                || "http".equalsIgnoreCase(type)
                || "sse".equalsIgnoreCase(type);
    }

    /** True when the configured type is the legacy {@code sse} spelling. */
    public boolean isLegacySse() {
        return "sse".equalsIgnoreCase(type);
    }

    public boolean isStdio() {
        return TYPE_STDIO.equalsIgnoreCase(type);
    }
}
