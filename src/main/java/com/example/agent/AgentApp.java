package com.example.agent;

import com.example.agent.agent.AgentAssistant;
import com.example.agent.agent.AgentFactory;
import com.example.agent.agent.StreamBridge;
import com.example.agent.config.AppConfig;
import com.example.agent.tools.McpBridge;
import com.example.agent.tui.AgentModel;
import com.example.agent.tui.CommandRegistry;
import com.williamcallahan.tui4j.compat.bubbletea.Program;

public class AgentApp {

    public static void main(String[] args) {
        // Load config — validates API keys before entering TUI
        AppConfig config = AppConfig.load();
        CommandRegistry.init(config);

        // Auto-connect to MCP server if configured
        McpBridge mcpBridge = null;
        String mcpUrl = config.getMcpAutoConnectUrl();
        if (!mcpUrl.isEmpty()) {
            mcpBridge = new McpBridge();
            String result = mcpBridge.connect(mcpUrl);
            System.err.println("[mcp] " + result);
            if (!mcpBridge.isConnected()) {
                System.err.println("[mcp] Auto-connect failed. You can retry with /mcp in the TUI.");
                mcpBridge = null;
            }
        }

        // Build AI assistant (with MCP tools if auto-connected)
        AgentAssistant assistant;
        if (mcpBridge != null && mcpBridge.isConnected()) {
            assistant = AgentFactory.create(config, mcpBridge.getToolProvider());
        } else {
            assistant = AgentFactory.create(config);
        }

        // Build TUI
        AgentModel model = new AgentModel();
        Program program = new Program(model).withAltScreen().withMouseCellMotion();

        // Wire the stream bridge, config, and pre-connected MCP bridge
        StreamBridge bridge = new StreamBridge(program, assistant);
        model.setStreamBridge(bridge);
        model.setAppConfig(config);
        if (mcpBridge != null) {
            model.setMcpBridge(mcpBridge);
        }

        program.run();
    }
}
