package com.example.agent;

import com.example.agent.config.AppConfig;
import com.example.agent.tui.AgentModel;
import com.williamcallahan.tui4j.compat.bubbletea.Program;

public class AgentApp {

  public static void main(String[] args) {
    // Load config first — validates API keys before entering TUI
    AppConfig config = AppConfig.load();

    new Program(new AgentModel())
        .withAltScreen()
        .run();
  }
}
