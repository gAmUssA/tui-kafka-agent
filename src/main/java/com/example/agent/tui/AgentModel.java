package com.example.agent.tui;

import com.example.agent.agent.AgentAssistant;
import com.example.agent.agent.AgentFactory;
import com.example.agent.agent.ModelSwitcher;
import com.example.agent.agent.StreamBridge;
import com.example.agent.config.AppConfig;
import com.example.agent.tools.McpBridge;
import com.williamcallahan.tui4j.compat.bubbles.spinner.Spinner;
import com.williamcallahan.tui4j.compat.bubbles.spinner.SpinnerType;
import com.williamcallahan.tui4j.compat.bubbles.textarea.Textarea;
import com.williamcallahan.tui4j.compat.bubbles.viewport.Viewport;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.message.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.WindowSizeMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Root TUI model implementing the Elm Architecture via tui4j.
 * Composes a Textarea for input and a Viewport for scrollable chat history.
 */
public class AgentModel implements Model {

  private final Textarea textarea;
  private final Viewport viewport;
  private final List<ChatEntry> chatHistory = new ArrayList<>();
  private Spinner spinner;
  private int width = 80;
  private int height = 24;

  private StreamBridge streamBridge;
  private AppConfig appConfig;
  private McpBridge mcpBridge;
  private boolean isStreaming;
  private boolean isToolExecuting;
  private String currentToolName;
  private StringBuilder currentResponse;
  private String activeModel;
  private boolean thinkingEnabled;

  private static final int INPUT_HEIGHT = 1;
  private static final int HEADER_HEIGHT = 2;

  public AgentModel() {
    textarea = new Textarea();
    textarea.setPlaceholder("Type a message... (/ for commands)");
    textarea.setWidth(78);
    textarea.setHeight(INPUT_HEIGHT);
    textarea.setShowLineNumbers(false);
    textarea.setCharLimit(0);
    textarea.focus();

    spinner = new Spinner(SpinnerType.DOT);

    viewport = Viewport.create(78, 18);
  }

  public void setStreamBridge(StreamBridge streamBridge) {
    this.streamBridge = streamBridge;
  }

  public void setAppConfig(AppConfig appConfig) {
    this.appConfig = appConfig;
    this.activeModel = appConfig.getAnthropicModel();
  }

  public void setMcpBridge(McpBridge mcpBridge) {
    this.mcpBridge = mcpBridge;
  }

  @Override
  public Command init() {
    // Deferred to init() — Style.render() requires terminal to be initialized
    textarea.setPrompt(Theme.INPUT_PROMPT.render("\u276F "));
    String name = (appConfig != null) ? appConfig.getAppName() : "kafka-agent";
    viewport.setContent(renderWelcome(name, 78));
    return textarea.init();
  }

  @Override
  public UpdateResult<? extends Model> update(Message msg) {
    // Handle tool executing
    if (msg instanceof ToolExecutingMessage(String toolName)) {
      isToolExecuting = true;
      currentToolName = toolName;
      refreshViewport();
      return UpdateResult.from(this, spinner.init());
    }

    // Handle tool completion
    if (msg instanceof ToolCompleteMessage(String toolName, String result1)) {
      isToolExecuting = false;
      chatHistory.add(ChatEntry.tool(toolName, result1));
      currentToolName = null;
      refreshViewport();
      return UpdateResult.from(this);
    }

    // Handle streaming tokens
    if (msg instanceof StreamTokenMessage(String token)) {
      if (currentResponse == null) {
        currentResponse = new StringBuilder();
      }
      currentResponse.append(token);
      refreshViewport();
      return UpdateResult.from(this);
    }

    // Handle stream completion
    if (msg instanceof StreamCompleteMessage(String response)) {
      isStreaming = false;
      if (!response.isBlank()) {
        chatHistory.add(ChatEntry.agent(response));
      }
      currentResponse = null;
      textarea.focus();
      refreshViewport();
      return UpdateResult.from(this);
    }

    // Handle errors
    if (msg instanceof ErrorMessage(String error)) {
      isStreaming = false;
      isToolExecuting = false;
      currentToolName = null;
      currentResponse = null;
      chatHistory.add(ChatEntry.error(error));
      textarea.focus();
      refreshViewport();
      return UpdateResult.from(this);
    }

    if (msg instanceof KeyPressMessage keyMsg) {
      // Quit on Ctrl+C
      if ("ctrl+c".equals(keyMsg.key())) {
        return UpdateResult.from(this, QuitMessage::new);
      }

      // Enter to submit message (intercept before textarea gets it)
      if ("enter".equals(keyMsg.key())) {
        if (isStreaming) {
          return UpdateResult.from(this);
        }
        String text = textarea.value().trim();
        if (!text.isEmpty()) {
          textarea.reset();

          // Try parsing as slash command
          Optional<SlashCommand> cmd = CommandParser.parse(text);
          if (cmd.isPresent()) {
            chatHistory.add(ChatEntry.user(text));
            handleSlashCommand(cmd.get());
            refreshViewport();
            return UpdateResult.from(this);
          }

          // Regular chat message
          chatHistory.add(ChatEntry.user(text));
          textarea.blur();
          isStreaming = true;
          currentResponse = new StringBuilder();
          refreshViewport();
          streamBridge.sendMessage(text);
        }
        return UpdateResult.from(this);
      }
    }

    // Handle window resize
    if (msg instanceof WindowSizeMessage(int width1, int height1)) {
      this.width = width1;
      this.height = height1;
      textarea.setWidth(width - 2);
      viewport.setWidth(width - 2);
      viewport.setHeight(height - INPUT_HEIGHT - HEADER_HEIGHT - 2);
      return UpdateResult.from(this);
    }

    // Forward spinner ticks when tool is executing
    if (isToolExecuting) {
      UpdateResult<Spinner> spinnerResult = spinner.update(msg);
      spinner = spinnerResult.model();
      refreshViewport();
      return UpdateResult.from(this, spinnerResult.command());
    }

    // Let viewport handle scroll keys (pgup, pgdn, etc.)
    viewport.update(msg);

    // Delegate all other input to textarea
    UpdateResult<? extends Model> result = textarea.update(msg);
    return UpdateResult.from(this, result.command());
  }

  @Override
  public String view() {
    int toolCount = (mcpBridge != null && mcpBridge.isConnected())
                    ? mcpBridge.getToolCount() : 0;
    String appName = (appConfig != null) ? appConfig.getAppName() : "kafka-agent";
    String header = HeaderView.render(appName, activeModel, toolCount, isStreaming, isToolExecuting, width);
    String separator = Theme.SEPARATOR.render("─".repeat(Math.max(1, width)));

    return header + "\n"
           + separator + "\n"
           + viewport.view() + "\n"
           + separator + "\n"
           + textarea.view();
  }

  private void handleSlashCommand(SlashCommand cmd) {
    switch (cmd.name()) {
      case "help" -> handleHelp();
      case "clear" -> handleClear();
      case "tools" -> handleTools();
      case "model" -> handleModel(cmd.args());
      case "thinking" -> handleThinking();
      case "mcp" -> handleMcpConnect(cmd.args().isEmpty() ? "" : cmd.args().getFirst());
      case "topics" -> handleTopicsShortcut();
      case "sql" -> handleSqlShortcut(cmd.args());
      case "reset-brewery" -> handleResetBrewery();
      default -> chatHistory.add(ChatEntry.error(
          "Unknown command: /" + cmd.name() + ". Type /help for available commands."));
    }
  }

  private void handleHelp() {
    String helpContent = """
        /help              Show this help message
        /clear             Clear chat history
        /tools             List available tools
        /model <name>      Switch model (sonnet, haiku, opus)
        /thinking          Toggle extended thinking mode
        /mcp <url>         Connect to MCP server
        /topics            List Kafka topics (via AI)
        /sql <query>       Run Flink SQL (via AI)
        /reset-brewery     Reset demo (delete brewery topics & Flink jobs)""";
    String helpBox = Theme.HELP_BOX.margin(0, 0, 0, 2).render(
        Theme.WELCOME_TITLE.render("Commands") + "\n\n" + helpContent);
    chatHistory.add(ChatEntry.prerendered(helpBox));
  }

  private void handleClear() {
    chatHistory.clear();
    // Rebuild assistant to reset chat memory
    if (appConfig != null) {
      var toolProvider = (mcpBridge != null && mcpBridge.isConnected())
                         ? mcpBridge.getToolProvider() : null;
      AgentAssistant newAssistant = AgentFactory.create(appConfig, toolProvider, activeModel, thinkingEnabled);
      streamBridge.setAssistant(newAssistant);
    }
    chatHistory.add(ChatEntry.tool("Chat cleared."));
  }

  private void handleTools() {
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.tool("No tools loaded. Use /mcp <url> to connect to an MCP server."));
      return;
    }
    var tools = mcpBridge.listToolNames();
    if (tools.isEmpty()) {
      chatHistory.add(ChatEntry.tool("Connected to MCP server but no tools available."));
    } else {
      var sb = new StringBuilder("Available tools (" + tools.size() + "):\n");
      for (String tool : tools) {
        sb.append("  - ").append(tool).append("\n");
      }
      chatHistory.add(ChatEntry.tool(sb.toString().stripTrailing()));
    }
  }

  private void handleModel(List<String> args) {
    if (args.isEmpty()) {
      chatHistory.add(ChatEntry.tool("Current model: " + activeModel
                                     + "\nUsage: /model <" + ModelSwitcher.availableModels() + ">"));
      return;
    }
    String shortName = args.getFirst();
    String resolved = ModelSwitcher.resolve(shortName);
    if (resolved == null) {
      chatHistory.add(ChatEntry.error(
          "Unknown model: " + shortName + ". Available: " + ModelSwitcher.availableModels()));
      return;
    }
    activeModel = resolved;
    if (appConfig != null) {
      var toolProvider = (mcpBridge != null && mcpBridge.isConnected())
                         ? mcpBridge.getToolProvider() : null;
      AgentAssistant newAssistant = AgentFactory.create(appConfig, toolProvider, activeModel, thinkingEnabled);
      streamBridge.setAssistant(newAssistant);
    }
    chatHistory.add(ChatEntry.tool("Switched to model: " + resolved));
  }

  private void handleThinking() {
    thinkingEnabled = !thinkingEnabled;
    if (appConfig != null) {
      var toolProvider = (mcpBridge != null && mcpBridge.isConnected())
                         ? mcpBridge.getToolProvider() : null;
      AgentAssistant newAssistant = AgentFactory.create(appConfig, toolProvider, activeModel, thinkingEnabled);
      streamBridge.setAssistant(newAssistant);
    }
    chatHistory.add(ChatEntry.tool("Extended thinking: " + (thinkingEnabled ? "ON" : "OFF")));
  }

  private void handleMcpConnect(String url) {
    if (url.isEmpty()) {
      chatHistory.add(ChatEntry.error("Usage: /mcp <url>"));
      return;
    }
    if (mcpBridge == null) {
      mcpBridge = new McpBridge();
    }
    String result = mcpBridge.connect(url);
    chatHistory.add(ChatEntry.tool(result));

    // Rebuild assistant with MCP tools if connected
    if (mcpBridge.isConnected() && appConfig != null) {
      AgentAssistant newAssistant = AgentFactory.create(appConfig, mcpBridge.getToolProvider(), activeModel, thinkingEnabled);
      streamBridge.setAssistant(newAssistant);
    }
  }

  private void handleTopicsShortcut() {
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.error("No MCP server connected. Use /mcp <url> first."));
      return;
    }
    // Send as AI request — the AI has the MCP tools to list topics
    textarea.blur();
    isStreaming = true;
    currentResponse = new StringBuilder();
    streamBridge.sendMessage("List all my Kafka topics.");
  }

  private void handleSqlShortcut(List<String> args) {
    if (args.isEmpty()) {
      chatHistory.add(ChatEntry.error("Usage: /sql <flink-sql-query>"));
      return;
    }
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.error("No MCP server connected. Use /mcp <url> first."));
      return;
    }
    String query = String.join(" ", args);
    textarea.blur();
    isStreaming = true;
    currentResponse = new StringBuilder();
    streamBridge.sendMessage("Execute this Flink SQL query: " + query);
  }

  private void handleResetBrewery() {
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.error("No MCP server connected. Use /mcp <url> first."));
      return;
    }
    textarea.blur();
    isStreaming = true;
    currentResponse = new StringBuilder();
    streamBridge.sendMessage(
        "Delete all brewery-* topics (brewery-sensors, brewery-alerts, brewery-metrics) "
        + "and delete any running Flink statements related to the brewery pipeline. "
        + "Also remove any brewery-pipeline tags. Confirm what was deleted.");
  }

  private void refreshViewport() {
    var sb = new StringBuilder();
    sb.append(ChatView.render(chatHistory, width));

    // Show spinner during tool execution
    if (isToolExecuting && currentToolName != null) {
      sb.append("  ").append(spinner.view())
          .append(Theme.SPINNER_TEXT.render(" Calling " + currentToolName + "()..."))
          .append("\n\n");
    }
    // Show partial response while streaming
    if (isStreaming && currentResponse != null && !currentResponse.isEmpty()) {
      sb.append("  ").append(Theme.AGENT_BADGE.render("Agent"))
          .append("  ").append(Theme.AGENT.render(currentResponse.toString()))
          .append(Theme.CURSOR.render("\u258C"))
          .append("\n\n");
    }
    viewport.setContent(sb.toString());
    viewport.gotoBottom();
  }

  private static String renderWelcome(String appName, int width) {
    String title = Theme.WELCOME_TITLE.render(appName);
    String subtitle = Theme.WELCOME_TEXT.render("AI-powered Kafka & Flink assistant");
    String hint1 = Theme.WELCOME_TEXT.render("Type a message to begin");
    String hint2 = Theme.WELCOME_TEXT.render("/help for available commands");

    String content = title + "\n\n" + subtitle + "\n\n" + hint1 + "\n" + hint2;
    int boxWidth = Math.min(width - 4, 50);
    return Theme.WELCOME_BOX.width(boxWidth).margin(1, 0, 1, 2).render(content);
  }
}
