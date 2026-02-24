package com.example.agent.tools;

import java.util.List;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;

/**
 * Connects to an MCP server via HTTP SSE transport and provides
 * discovered tools to the AI agent via McpToolProvider.
 */
public class McpBridge {

    private McpClient mcpClient;
    private McpToolProvider toolProvider;
    private String connectedUrl;
    private int toolCount;

    /**
     * Connect to an MCP server at the given SSE URL.
     *
     * @return a status message describing the result
     */
    public String connect(String sseUrl) {
        try {
            // Close any existing connection
            if (mcpClient != null) {
                try {
                    mcpClient.close();
                } catch (Exception ignored) {
                }
            }

            McpTransport transport = HttpMcpTransport.builder()
                    .sseUrl(sseUrl)
                    .build();

            mcpClient = DefaultMcpClient.builder()
                    .key("kafka-agent-mcp")
                    .transport(transport)
                    .build();

            toolProvider = McpToolProvider.builder()
                    .mcpClients(mcpClient)
                    .build();

            // Count tools by listing them
            toolCount = mcpClient.listTools().size();
            connectedUrl = sseUrl;

            return "Connected to MCP server. Discovered " + toolCount + " tools.";
        } catch (Exception e) {
            mcpClient = null;
            toolProvider = null;
            connectedUrl = null;
            toolCount = 0;
            return "Failed to connect to MCP server: " + e.getMessage();
        }
    }

    public boolean isConnected() {
        return mcpClient != null;
    }

    public String getConnectedUrl() {
        return connectedUrl;
    }

    public int getToolCount() {
        return toolCount;
    }

    public ToolProvider getToolProvider() {
        return toolProvider;
    }

    public List<String> listToolNames() {
        if (mcpClient == null) {
            return List.of();
        }
        return mcpClient.listTools().stream()
                .map(tool -> tool.name())
                .toList();
    }
}
