package com.example.agent;

import com.example.agent.agent.ConversationBridge;
import com.example.agent.agent.ConversationFactory;
import com.example.agent.config.AppConfig;
import com.example.agent.config.McpServerConfig;
import com.example.agent.tools.McpBridge;
import com.example.agent.tui.AgentEventMessage;
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

        AgentModel model = new AgentModel();

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

        // AgentEvents are produced on LangChain4j callback threads; Program.send
        // marshals them onto the tui4j event loop. The factory owns the
        // session-scoped UsageTracker and AgentMonitor across rebuilds.
        ConversationFactory factory = new ConversationFactory(
                config, event -> program.send(new AgentEventMessage(event)), model.getUsageTracker());

        boolean agentic = config.isAgenticEnabled();
        ConversationBridge bridge = factory.create(
                mcpBridge, config.getProvider(), null, false, agentic);
        System.err.println("[agent] mode: " + bridge.modeLabel());

        model.setAppConfig(config);
        model.setConversationFactory(factory, agentic);
        model.setConversationBridge(bridge);
        if (mcpBridge != null) {
            model.setMcpBridge(mcpBridge);
        }

        program.run();
    }
}
