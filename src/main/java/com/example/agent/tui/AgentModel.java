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
  private final List<ChatEntry> pendingToolResults = new ArrayList<>();
  private Spinner spinner;
  private int width = 120;
  private int height = 32;

  private StreamBridge streamBridge;
  private AppConfig appConfig;
  private McpBridge mcpBridge;
  private boolean isStreaming;
  private boolean isToolExecuting;
  private String currentToolName;
  private StringBuilder currentResponse;
  private String activeModel;
  private boolean thinkingEnabled;
  private boolean toolOutputExpanded;
  private boolean quitRequested;

  // Suggestion popup state
  private boolean showSuggestions;
  private List<CommandRegistry.CommandInfo> filteredSuggestions = List.of();
  private int selectedSuggestion;

  private static final int INPUT_HEIGHT = 3;
  private static final int HEADER_HEIGHT = 2;

  public AgentModel() {
    textarea = new Textarea();
    textarea.setPlaceholder("Type a message... (/ for commands)");
    textarea.setWidth(118);
    textarea.setHeight(INPUT_HEIGHT);
    textarea.setShowLineNumbers(false);
    textarea.setCharLimit(0);
    textarea.focus();

    spinner = new Spinner(SpinnerType.DOT);

    viewport = Viewport.create(118, 24);
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
    viewport.setContent(renderWelcome(name, 118));
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

    // Handle tool completion — buffer results until stream completes
    if (msg instanceof ToolCompleteMessage(String toolName, String result1)) {
      isToolExecuting = false;
      currentToolName = null;
      pendingToolResults.add(ChatEntry.tool(toolName, result1));
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
      // Flush buffered tool results AFTER the agent response
      chatHistory.addAll(pendingToolResults);
      pendingToolResults.clear();
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
      // Flush any buffered tool results so they're not lost
      chatHistory.addAll(pendingToolResults);
      pendingToolResults.clear();
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

      // Toggle tool output collapsed/expanded
      if ("ctrl+o".equals(keyMsg.key())) {
        toolOutputExpanded = !toolOutputExpanded;
        refreshViewport();
        return UpdateResult.from(this);
      }

      // --- Suggestion popup key interception (before textarea) ---
      if (showSuggestions && !filteredSuggestions.isEmpty()) {
        switch (keyMsg.key()) {
          case "tab" -> {
            // Accept selected suggestion → fill textarea
            var selected = filteredSuggestions.get(selectedSuggestion);
            String completion = "/" + selected.name() + (selected.hasArgs() ? " " : "");
            textarea.setValue(completion);
            dismissSuggestions();
            return UpdateResult.from(this);
          }
          case "up" -> {
            selectedSuggestion = Math.max(0, selectedSuggestion - 1);
            return UpdateResult.from(this);
          }
          case "down" -> {
            selectedSuggestion = Math.min(filteredSuggestions.size() - 1, selectedSuggestion + 1);
            return UpdateResult.from(this);
          }
          case "esc" -> {
            dismissSuggestions();
            return UpdateResult.from(this);
          }
          case "enter" -> {
            // Dismiss suggestions, then fall through to submit
            dismissSuggestions();
          }
          default -> {
            // Other keys: fall through to normal handling (textarea gets them)
          }
        }
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
            if (quitRequested) {
              return UpdateResult.from(this, QuitMessage::new);
            }
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

    // Update suggestion state after textarea processes the key
    updateSuggestions();

    return UpdateResult.from(this, result.command());
  }

  @Override
  public String view() {
    int toolCount = (mcpBridge != null && mcpBridge.isConnected())
                    ? mcpBridge.getToolCount() : 0;
    String appName = (appConfig != null) ? appConfig.getAppName() : "kafka-agent";
    String header = HeaderView.render(appName, activeModel, toolCount, isStreaming, isToolExecuting, width);
    String separator = Theme.SEPARATOR.render("─".repeat(Math.max(1, width)));

    String suggestionPopup = (showSuggestions && !filteredSuggestions.isEmpty())
        ? CommandSuggestionView.render(filteredSuggestions, selectedSuggestion, width) + "\n"
        : "";

    return header + "\n"
           + separator + "\n"
           + viewport.view() + "\n"
           + separator + "\n"
           + suggestionPopup
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
      case "quit" -> quitRequested = true;
      case "setup" -> handleDemoCommand(
          appConfig != null ? appConfig.getDemoSetupPrompt() : null, "setup");
      case "reset" -> handleDemoCommand(
          appConfig != null ? appConfig.getDemoResetPrompt() : null, "reset");
      default -> chatHistory.add(ChatEntry.error(
          "Unknown command: /" + cmd.name() + ". Type /help for available commands."));
    }
  }

  private void handleHelp() {
    // Build help content from CommandRegistry (single source of truth)
    int maxCmdLen = CommandRegistry.ALL.stream()
        .mapToInt(c -> c.displayName().length())
        .max().orElse(10);
    var helpSb = new StringBuilder();
    for (var cmd : CommandRegistry.ALL) {
      helpSb.append(String.format("    %-" + (maxCmdLen + 4) + "s %s\n", cmd.displayName(), cmd.description()));
    }
    // Remove trailing newline
    String helpContent = helpSb.toString().stripTrailing();
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

  private void handleDemoCommand(String prompt, String commandName) {
    if (prompt == null) {
      chatHistory.add(ChatEntry.error(
          "/" + commandName + " is not configured. Add demo." + commandName
          + " to your config YAML."));
      return;
    }
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.error("No MCP server connected. Use /mcp <url> first."));
      return;
    }
    textarea.blur();
    isStreaming = true;
    currentResponse = new StringBuilder();
    streamBridge.sendMessage(prompt);
  }

  /**
   * Update suggestion popup state based on current textarea value.
   * Activates when text starts with '/' and has no space (still typing command name).
   */
  private void updateSuggestions() {
    String val = textarea.value();
    if (val.startsWith("/") && !val.contains(" ")) {
      String prefix = val.substring(1); // text after '/'
      filteredSuggestions = CommandRegistry.filter(prefix);
      showSuggestions = !filteredSuggestions.isEmpty();
      // Clamp selection to valid range
      selectedSuggestion = Math.min(selectedSuggestion, Math.max(0, filteredSuggestions.size() - 1));
    } else {
      dismissSuggestions();
    }
  }

  private void dismissSuggestions() {
    showSuggestions = false;
    filteredSuggestions = List.of();
    selectedSuggestion = 0;
  }

  private void refreshViewport() {
    var sb = new StringBuilder();
    sb.append(ChatView.render(chatHistory, width, toolOutputExpanded));

    // Show pending tool results (buffered, will be committed to history on stream complete)
    for (ChatEntry toolEntry : pendingToolResults) {
      if (toolEntry.toolName() != null) {
        if (toolOutputExpanded) {
          sb.append(ToolResultView.render(toolEntry.toolName(), toolEntry.content(), width - 4));
        } else {
          sb.append(ToolResultView.renderCollapsed(toolEntry.toolName(), toolEntry.content(), width - 4));
        }
      } else {
        sb.append("  ").append(Theme.TOOL_BADGE.render("System"));
        sb.append("  ").append(Theme.TOOL.render(toolEntry.content()));
      }
      sb.append("\n\n");
    }

    // Show spinner during tool execution
    if (isToolExecuting && currentToolName != null) {
      sb.append("  ").append(spinner.view())
          .append(Theme.SPINNER_TEXT.render(" Calling " + currentToolName + "()..."))
          .append("\n\n");
    }
    // Show partial response while streaming
    if (isStreaming && currentResponse != null && !currentResponse.isEmpty()) {
      sb.append("  ").append(Theme.AGENT_BADGE.render("Agent")).append("\n");
      String[] lines = currentResponse.toString().split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        sb.append("  ").append(Theme.AGENT.render(lines[i]));
        if (i == lines.length - 1) {
          sb.append(Theme.CURSOR.render("\u258C"));
        }
        sb.append("\n");
      }
      sb.append("\n");
    }
    viewport.setContent(sb.toString());
    viewport.gotoBottom();
  }

  public void cleanup() {
    if (mcpBridge != null) {
      mcpBridge.close();
    }
  }

  private static String renderWelcome(String appName, int width) {
    String title = Theme.WELCOME_TITLE.render(appName);
    String subtitle = Theme.WELCOME_TEXT.render("AI-powered Kafka & Flink assistant");
    String hint1 = Theme.WELCOME_TEXT.render("Type a message to begin");
    String hint2 = Theme.WELCOME_TEXT.render("/help for available commands");
    String hint3 = Theme.WELCOME_TEXT.render("Ctrl+O to show/hide tool output");

    String content = title + "\n\n" + subtitle + "\n\n" + hint1 + "\n" + hint2 + "\n" + hint3;
    int boxWidth = Math.min(width - 4, 72);
    return Theme.WELCOME_BOX.width(boxWidth).margin(1, 0, 1, 2).render(content);
  }
}
