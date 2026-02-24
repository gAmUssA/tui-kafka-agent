package com.example.agent;

import com.example.agent.agent.AgentAssistant;
import com.example.agent.agent.AgentFactory;
import com.example.agent.agent.StreamBridge;
import com.example.agent.config.AppConfig;
import com.example.agent.tui.AgentModel;
import com.williamcallahan.tui4j.compat.bubbletea.Program;

public class AgentApp {

    public static void main(String[] args) {
        // Load config — validates API keys before entering TUI
        AppConfig config = AppConfig.load();

        // Build AI assistant
        AgentAssistant assistant = AgentFactory.create(config);

        // Build TUI
        AgentModel model = new AgentModel();
        Program program = new Program(model).withAltScreen();

        // Wire the stream bridge (needs both Program and Assistant)
        StreamBridge bridge = new StreamBridge(program, assistant);
        model.setStreamBridge(bridge);

        program.run();
    }
}
