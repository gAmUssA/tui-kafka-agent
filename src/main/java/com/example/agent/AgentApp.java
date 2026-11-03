package com.example.agent;

import com.example.agent.config.AppConfig;
import com.example.agent.config.McpServerConfig;
import com.example.agent.tools.McpBridge;
import com.example.agent.tui2.BreweryApp;

import java.util.Map;

public class AgentApp {

    public static void main(String[] args) {
        configureLogging();

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

        runTamboui(config, mcpBridge);
    }

    /**
     * Send SLF4J output to a file instead of stderr. Must run before the first
     * logger is created (the simple logger reads its properties once). The
     * level comes from {@code simplelogger.properties} (WARN) unless
     * {@code KAFKA_AGENT_LOG_LEVEL} overrides it; the file is
     * {@code ~/.config/kafka-agent/kafka-agent.log} (or {@code KAFKA_AGENT_LOG}).
     */
    private static void configureLogging() {
        String file = System.getenv("KAFKA_AGENT_LOG");
        if (file == null || file.isBlank()) {
            file = java.nio.file.Path.of(System.getProperty("user.home"), ".config", "kafka-agent", "kafka-agent.log").toString();
        }
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(file).getParent());
        } catch (Exception ignored) {
            file = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "kafka-agent.log").toString();
        }
        System.setProperty("org.slf4j.simpleLogger.logFile", file);
        String level = System.getenv("KAFKA_AGENT_LOG_LEVEL");
        if (level != null && !level.isBlank()) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", level.trim().toLowerCase());
        }
        System.err.println("[log] " + file);
    }

    /**
     * Three-panel TamboUI front end (chat │ activity, prompt). Same agent layer,
     * same ConversationFactory; only the sink adapter differs.
     */
    private static void runTamboui(AppConfig config, McpBridge mcpBridge) {
        BreweryApp app = new BreweryApp(config, mcpBridge, config.isAgenticEnabled());
        System.err.println("[agent] ui: tamboui, mode: " + app.modeLabel());
        try {
            app.run();
        } catch (Exception e) {
            System.err.println("[tui] " + e.getMessage());
        }
    }
}
