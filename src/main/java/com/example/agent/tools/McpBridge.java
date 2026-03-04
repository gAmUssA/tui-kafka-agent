package com.example.agent.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.agent.config.McpServerConfig;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;

/**
 * Manages multiple simultaneous MCP server connections.
 * Each server is identified by a unique name. Tools from all
 * connected servers are aggregated into a single {@link McpToolProvider}.
 */
public class McpBridge {

    private final Map<String, McpClient> clients = new LinkedHashMap<>();
    private McpToolProvider toolProvider;

    /**
     * Connect to an MCP server using a typed configuration.
     *
     * @param name   unique server name
     * @param config transport configuration
     * @return status message
     */
    public String connect(String name, McpServerConfig config) {
        try {
            // Disconnect existing connection with same name
            disconnectQuietly(name);

            McpTransport transport = createTransport(config);

            McpClient client = DefaultMcpClient.builder()
                    .key(name)
                    .transport(transport)
                    .build();

            clients.put(name, client);
            rebuildToolProvider();

            int toolCount = client.listTools().size();
            return "Connected to '" + name + "' (" + config.type() + "). Discovered " + toolCount + " tools.";
        } catch (Exception e) {
            clients.remove(name);
            rebuildToolProvider();
            return "Failed to connect to '" + name + "': " + e.getMessage();
        }
    }

    /**
     * Convenience method to connect an SSE server at runtime (e.g. from /mcp command).
     */
    public String connectSse(String name, String sseUrl) {
        return connect(name, new McpServerConfig("sse", sseUrl, null, Collections.emptyMap()));
    }

    /**
     * Disconnect a specific server by name.
     *
     * @return status message
     */
    public String disconnect(String name) {
        McpClient client = clients.remove(name);
        if (client == null) {
            return "No server connected with name '" + name + "'.";
        }
        closeQuietly(client);
        rebuildToolProvider();
        return "Disconnected from '" + name + "'.";
    }

    /**
     * Disconnect all connected servers.
     *
     * @return status message
     */
    public String disconnectAll() {
        if (clients.isEmpty()) {
            return "No servers connected.";
        }
        int count = clients.size();
        clients.values().forEach(this::closeQuietly);
        clients.clear();
        rebuildToolProvider();
        return "Disconnected from " + count + " server(s).";
    }

    public boolean isConnected() {
        return !clients.isEmpty();
    }

    /**
     * Returns total tool count across all connected servers.
     */
    public int getToolCount() {
        return clients.values().stream()
                .mapToInt(c -> {
                    try {
                        return c.listTools().size();
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
    }

    public ToolProvider getToolProvider() {
        return toolProvider;
    }

    /**
     * Returns connected server names with their tool counts.
     */
    public Map<String, Integer> getConnectedServers() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : clients.entrySet()) {
            try {
                result.put(entry.getKey(), entry.getValue().listTools().size());
            } catch (Exception e) {
                result.put(entry.getKey(), 0);
            }
        }
        return result;
    }

    /**
     * Returns a flat list of all tool names across all servers.
     */
    public List<String> listToolNames() {
        List<String> names = new ArrayList<>();
        for (McpClient client : clients.values()) {
            try {
                client.listTools().forEach(t -> names.add(t.name()));
            } catch (Exception ignored) {
            }
        }
        return names;
    }

    /**
     * Returns tool names grouped by server name.
     */
    public Map<String, List<String>> listToolNamesByServer() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var entry : clients.entrySet()) {
            try {
                List<String> names = entry.getValue().listTools().stream()
                        .map(t -> t.name())
                        .toList();
                result.put(entry.getKey(), names);
            } catch (Exception e) {
                result.put(entry.getKey(), List.of());
            }
        }
        return result;
    }

    /**
     * Close all connections and release resources.
     */
    public void close() {
        clients.values().forEach(this::closeQuietly);
        clients.clear();
        toolProvider = null;
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    static McpTransport createTransport(McpServerConfig config) {
        if (config.isStdio()) {
            if (config.command() == null || config.command().isEmpty()) {
                throw new IllegalArgumentException("stdio transport requires a 'command' list");
            }
            var builder = StdioMcpTransport.builder()
                    .command(config.command());
            if (config.env() != null && !config.env().isEmpty()) {
                builder.environment(config.env());
            }
            return builder.build();
        }

        // Default: SSE
        if (config.url() == null || config.url().isBlank()) {
            throw new IllegalArgumentException("SSE transport requires a 'url'");
        }
        return HttpMcpTransport.builder()
                .sseUrl(config.url())
                .build();
    }

    private void rebuildToolProvider() {
        if (clients.isEmpty()) {
            toolProvider = null;
            return;
        }
        toolProvider = McpToolProvider.builder()
                .mcpClients(new ArrayList<>(clients.values()))
                .build();
    }

    private void disconnectQuietly(String name) {
        McpClient existing = clients.remove(name);
        if (existing != null) {
            closeQuietly(existing);
        }
    }

    private void closeQuietly(McpClient client) {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }
}
