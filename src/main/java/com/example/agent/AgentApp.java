package com.example.agent;

import com.example.agent.agent.AgentAssistant;
import com.example.agent.agent.AgentFactory;
import com.example.agent.agent.StreamBridge;
import com.example.agent.config.AppConfig;
import com.example.agent.config.McpServerConfig;
import com.example.agent.tools.McpBridge;
import com.example.agent.tui.AgentModel;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.Map;

public class AgentApp {

    public static void main(String[] args) {
        // Load config — validates API keys before entering TUI
        AppConfig config = AppConfig.load();

        // Auto-connect to MCP servers if configured
        McpBridge mcpBridge = null;
        Map<String, McpServerConfig> mcpServers = config.getMcpServers();
        if (!mcpServers.isEmpty()) {
            mcpBridge = new McpBridge();
            for (Map.Entry<String, McpServerConfig> entry : mcpServers.entrySet()) {
                String result = mcpBridge.connect(entry.getKey(), entry.getValue());
                System.err.println("[mcp] " + result);
            }
            if (!mcpBridge.isConnected()) {
                System.err.println("[mcp] Auto-connect failed. You can retry with /mcp in the TUI.");
                mcpBridge = null;
            }
        }

        // Build TUI model first so we can grab its session-scoped UsageTracker
        // and register it on the initial assistant. Otherwise the listener
        // would only attach on the first /model rebuild — first request misses.
        AgentModel model = new AgentModel();
        var usageTracker = model.getUsageTracker();

        // Build AI assistant (with MCP tools if auto-connected, with usage tracker always)
        var toolProvider = (mcpBridge != null && mcpBridge.isConnected())
                ? mcpBridge.getToolProvider()
                : null;
        AgentAssistant assistant = AgentFactory.create(
                config, toolProvider, config.getProvider(), null, false, usageTracker);

        // Query the actual terminal size via JLine before starting Program.
        // tui4j 0.3.3 does not reliably emit a WindowSizeMessage on startup
        // (especially under tmux), so without this the UI renders at the
        // default 80x24 until the user manually resizes.
        try (Terminal terminal = TerminalBuilder.builder().system(true).dumb(true).build()) {
            int w = terminal.getWidth();
            int h = terminal.getHeight();
            if (w > 0 && h > 0) {
                model.setInitialSize(w, h);
            }
        } catch (Exception e) {
            System.err.println("[tui] Could not detect terminal size: " + e.getMessage());
        }

        // withMouseCellMotion enables SGR 1006 extended mouse mode, which tmux
        // and other multiplexers require to forward wheel events to the app.
        // Without this, mouse wheel works in iTerm/Terminal.app but not tmux.
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
