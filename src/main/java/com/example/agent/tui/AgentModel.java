package com.example.agent.tui;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.ConversationBridge;
import com.example.agent.agent.ConversationFactory;
import com.example.agent.agent.ModelSwitcher;
import com.example.agent.agent.OllamaModelDiscovery;
import com.example.agent.agent.Provider;
import com.example.agent.agent.UsageTracker;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseButton;
import com.williamcallahan.tui4j.compat.bubbletea.input.MouseMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.WindowSizeMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private int width = 80;
  private int height = 24;

  private ConversationBridge streamBridge;
  private ConversationFactory conversationFactory;
  private boolean agenticEnabled;
  private AppConfig appConfig;
  private McpBridge mcpBridge;
  private boolean isStreaming;
  private boolean isToolExecuting;
  private String currentToolName;
  /** Name of the agent currently working, shown next to the spinner; null when idle. */
  private String currentAgentName;
  /** Stack of agents that have started but not finished (sub-agents nest). */
  private final List<String> activeAgents = new ArrayList<>();
  /** Agent name StreamBridge uses for the single, non-agentic assistant. */
  private static final String SINGLE_ASSISTANT = "assistant";
  private StringBuilder currentResponse;
  private Provider activeProvider;
  private String activeModel;
  private final OllamaModelDiscovery ollamaDiscovery = new OllamaModelDiscovery();
  private final UsageTracker usageTracker = new UsageTracker();
  private boolean thinkingEnabled;
  private boolean toolOutputExpanded;
  private boolean quitRequested;

  // Suggestion popup state
  private boolean showSuggestions;
  private List<CommandRegistry.CommandInfo> filteredSuggestions = List.of();
  private int selectedSuggestion;

  private static final int INPUT_HEIGHT = 3;
  // header bar + usage line + separator beneath them
  private static final int HEADER_HEIGHT = 3;
  private static final int CHROME_HEIGHT = INPUT_HEIGHT + HEADER_HEIGHT + 2;
  private static final int HORIZONTAL_PADDING = 2;
  // Two-space indent on each side for content rendered inside the viewport
  // (badge + indented body). Box-rendering helpers must subtract this from
  // contentWidth() to avoid horizontal overflow.
  private static final int INDENT_PADDING = 2;
  private static final int MIN_SUPPORTED_WIDTH = 40;
  private static final int MIN_SUPPORTED_HEIGHT = 10;
  private static final int WELCOME_BOX_MAX_WIDTH = 84;

  private int contentWidth() {
    return Math.max(1, width - HORIZONTAL_PADDING);
  }

  /** Width available inside an indented content box (tool result, markdown, welcome). */
  private int innerContentWidth() {
    return Math.max(1, contentWidth() - INDENT_PADDING);
  }

  private int viewportHeight() {
    return Math.max(1, height - CHROME_HEIGHT);
  }

  private boolean terminalTooSmall() {
    return width < MIN_SUPPORTED_WIDTH || height < MIN_SUPPORTED_HEIGHT;
  }

  public AgentModel() {
    textarea = new Textarea();
    textarea.setPlaceholder("Type a message... (/ for commands)");
    textarea.setWidth(contentWidth());
    textarea.setHeight(INPUT_HEIGHT);
    textarea.setShowLineNumbers(false);
    textarea.setCharLimit(0);
    textarea.focus();

    spinner = new Spinner(SpinnerType.DOT);

    viewport = Viewport.create(contentWidth(), viewportHeight());
    viewport.setMouseWheelEnabled(true);
    viewport.setMouseWheelDelta(3);
  }

  public void setConversationBridge(ConversationBridge bridge) {
    this.streamBridge = bridge;
  }

  /** Factory used to rebuild the bridge on /model, /thinking, /mcp, /agentic, /clear. */
  public void setConversationFactory(ConversationFactory factory, boolean agenticEnabled) {
    this.conversationFactory = factory;
    this.agenticEnabled = agenticEnabled;
  }

  public void setAppConfig(AppConfig appConfig) {
    this.appConfig = appConfig;
    this.activeProvider = appConfig.getProvider();
    this.activeModel = (activeProvider == Provider.OLLAMA)
        ? appConfig.getOllamaModel()
        : appConfig.getAnthropicModel();
  }

  public void setMcpBridge(McpBridge mcpBridge) {
    this.mcpBridge = mcpBridge;
  }

  /**
   * Returns the session-scoped {@link UsageTracker} so {@link com.example.agent.AgentApp}
   * can register it on the initial assistant — built before AgentModel's
   * setters are wired and would otherwise miss the first request.
   */
  public UsageTracker getUsageTracker() {
    return usageTracker;
  }

  /**
   * Seed the initial terminal size before {@link Program#run()} starts.
   * tui4j 0.3.3 does not reliably emit a {@code WindowSizeMessage} on startup
   * under some terminals (notably tmux), so the constructor's 80x24 defaults
   * would otherwise render until the user manually resizes. Call this with
   * dimensions queried from JLine before constructing the {@link Program}.
   */
  public void setInitialSize(int width, int height) {
    if (width > 0) {
      this.width = width;
    }
    if (height > 0) {
      this.height = height;
    }
    textarea.setWidth(contentWidth());
    viewport.setWidth(contentWidth());
    viewport.setHeight(viewportHeight());
  }

  @Override
  public Command init() {
    // Deferred to init() — Style.render() requires terminal to be initialized
    textarea.setPrompt(Theme.INPUT_PROMPT.render("\u276F "));
    String name = (appConfig != null) ? appConfig.getAppName() : "kafka-agent";
    viewport.setContent(renderWelcome(name, innerContentWidth()));
    return textarea.init();
  }

  @Override
  public UpdateResult<? extends Model> update(Message msg) {
    if (msg instanceof AgentEventMessage(AgentEvent event)) {
      return onAgentEvent(event);
    }

    if (msg instanceof KeyPressMessage keyMsg) {
      // Quit on Ctrl+C
      if ("ctrl+c".equals(keyMsg.key())) {
        return UpdateResult.from(this, Command.quit());
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

      // Esc quits when no suggestion popup is active (popup ESC handler above
      // returns early when visible, so this only fires in the default state).
      if ("esc".equals(keyMsg.key())) {
        return UpdateResult.from(this, Command.quit());
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
              return UpdateResult.from(this, Command.quit());
            }
            refreshViewport();
            return UpdateResult.from(this);
          }

          // Regular chat message — currentResponse lazily initialized
          // by the AgentEvent.Token handler on first token.
          chatHistory.add(ChatEntry.user(text));
          textarea.blur();
          isStreaming = true;
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
      textarea.setWidth(contentWidth());
      viewport.setWidth(contentWidth());
      viewport.setHeight(viewportHeight());
      // Re-render content with new dimensions
      if (chatHistory.isEmpty() && !isStreaming) {
        String name = (appConfig != null) ? appConfig.getAppName() : "kafka-agent";
        viewport.setContent(renderWelcome(name, innerContentWidth()));
      } else {
        refreshViewport();
      }
      return UpdateResult.from(this);
    }

    // Handle mouse wheel explicitly — viewport's native handler does bounds
    // checking against its own Y position, but our viewport renders below a
    // header so the bounds check fails. Drive scrolling directly instead.
    if (msg instanceof MouseMessage mouse) {
      if (mouse.isWheel()) {
        if (mouse.getButton() == MouseButton.MouseButtonWheelUp) {
          viewport.scrollUp(3);
        } else if (mouse.getButton() == MouseButton.MouseButtonWheelDown) {
          viewport.scrollDown(3);
        }
      }
      // Consume all mouse events — don't let them leak into textarea/spinner
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

  /**
   * Single entry point for everything the agent layer reports. Exhaustive over
   * the sealed {@link AgentEvent} hierarchy so a new event type is a compile
   * error here rather than a silently ignored message.
   */
  private UpdateResult<? extends Model> onAgentEvent(AgentEvent event) {
    switch (event) {
      case AgentEvent.AgentStarted(String agent) -> {
        currentAgentName = agent;
        activeAgents.add(agent);
        refreshViewport();
        return UpdateResult.from(this);
      }
      case AgentEvent.AgentFinished(String agent, Object output, long durationMs) -> {
        activeAgents.remove(agent);
        currentAgentName = activeAgents.isEmpty() ? null : activeAgents.getLast();
        // The single assistant finishing is the turn itself; sub-agents get a trace line.
        if (!SINGLE_ASSISTANT.equals(agent)) {
          pendingToolResults.add(ChatEntry.tool(
              "↳ " + agent + " done in " + formatDuration(durationMs)));
        }
        refreshViewport();
        return UpdateResult.from(this);
      }
      case AgentEvent.ToolStarted(String agent, String tool, String arguments) -> {
        isToolExecuting = true;
        currentToolName = tool;
        // The model often narrates ("Let me check...") before calling a tool and then
        // continues after it; separate the two round-trips so they don't run together.
        if (currentResponse != null && !currentResponse.isEmpty()
            && currentResponse.charAt(currentResponse.length() - 1) != '\n') {
          currentResponse.append("\n\n");
        }
        refreshViewport();
        return UpdateResult.from(this, spinner.init());
      }
      case AgentEvent.ToolFinished(String agent, String tool, String result) -> {
        isToolExecuting = false;
        currentToolName = null;
        // Buffer tool results until the answer completes so they render after it.
        pendingToolResults.add(ChatEntry.tool(tool, result));
        refreshViewport();
        return UpdateResult.from(this);
      }
      case AgentEvent.Token(String text) -> {
        if (currentResponse == null) {
          currentResponse = new StringBuilder();
        }
        currentResponse.append(text);
        refreshViewport();
        return UpdateResult.from(this);
      }
      case AgentEvent.Completed(String fullText) -> {
        isStreaming = false;
        isToolExecuting = false;
        currentToolName = null;
        currentAgentName = null;
        activeAgents.clear();
        if (!fullText.isBlank()) {
          chatHistory.add(ChatEntry.agent(fullText));
        }
        flushPendingToolResults();
        currentResponse = null;
        textarea.focus();
        refreshViewport();
        return UpdateResult.from(this);
      }
      case AgentEvent.Failed failed -> {
        isStreaming = false;
        isToolExecuting = false;
        currentToolName = null;
        currentAgentName = null;
        activeAgents.clear();
        currentResponse = null;
        flushPendingToolResults();
        String where = (failed.agent() == null || SINGLE_ASSISTANT.equals(failed.agent()))
            ? "" : "[" + failed.agent() + "] ";
        chatHistory.add(ChatEntry.error(where + failed.message()));
        textarea.focus();
        refreshViewport();
        return UpdateResult.from(this);
      }
    }
  }

  private void flushPendingToolResults() {
    chatHistory.addAll(pendingToolResults);
    pendingToolResults.clear();
  }

  private static String formatDuration(long ms) {
    return ms < 1000 ? ms + " ms" : String.format("%.1f s", ms / 1000.0);
  }

  @Override
  public String view() {
    if (terminalTooSmall()) {
      return renderTooSmallNotice();
    }
    int toolCount = (mcpBridge != null && mcpBridge.isConnected())
                    ? mcpBridge.getToolCount() : 0;
    int serverCount = (mcpBridge != null) ? mcpBridge.getConnectedServers().size() : 0;
    String appName = (appConfig != null) ? appConfig.getAppName() : "kafka-agent";
    String mode = (streamBridge != null) ? streamBridge.modeLabel() : "";
    String header = HeaderView.render(appName, activeProvider, activeModel, mode, toolCount, serverCount, isStreaming, isToolExecuting, width);
    String usage = HeaderView.renderUsage(usageTracker.snapshot());
    String separator = Theme.SEPARATOR.render("─".repeat(Math.max(1, width)));

    String suggestionPopup = (showSuggestions && !filteredSuggestions.isEmpty())
        ? CommandSuggestionView.render(filteredSuggestions, selectedSuggestion, width) + "\n"
        : "";

    return header + "\n"
           + usage + "\n"
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
      case "mcp" -> handleMcp(cmd.args());
      case "usage" -> handleUsage(cmd.args());
      case "agentic" -> handleAgentic(cmd.args());
      case "report" -> handleReport();
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
    if (streamBridge != null) {
      streamBridge.reset();   // evicts the persistent AgenticScope in agentic mode
    }
    rebuildAssistant();
    chatHistory.add(ChatEntry.tool("Chat cleared."));
  }

  private void handleAgentic(List<String> args) {
    if (args.isEmpty()) {
      chatHistory.add(ChatEntry.tool("Agentic mode is " + (agenticEnabled ? "ON" : "OFF")
          + " (supervisor + brewmaster/confluent/flink specialists + presenter).\n"
          + "Usage: /agentic on | off"));
      return;
    }
    switch (args.getFirst().toLowerCase()) {
      case "on" -> agenticEnabled = true;
      case "off" -> agenticEnabled = false;
      default -> {
        chatHistory.add(ChatEntry.error("Usage: /agentic on | off"));
        return;
      }
    }
    rebuildAssistant();
    chatHistory.add(ChatEntry.tool("Agentic mode " + (agenticEnabled ? "ON" : "OFF")
        + " — now using " + (streamBridge != null ? streamBridge.modeLabel() : "?") + " bridge."));
  }

  /** Write the AgentMonitor's topology + timeline report to an HTML file and open it. */
  private void handleReport() {
    if (conversationFactory == null) {
      chatHistory.add(ChatEntry.error("No conversation factory wired."));
      return;
    }
    var monitor = conversationFactory.monitor();
    int runs = monitor.successfulExecutions().size() + monitor.failedExecutions().size();
    if (runs == 0) {
      chatHistory.add(ChatEntry.tool("No agentic executions recorded yet. Run a turn with /agentic on first."));
      return;
    }
    try {
      Path dir = Path.of(System.getProperty("java.io.tmpdir"), "kafka-agent-reports");
      Files.createDirectories(dir);
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      Path file = dir.resolve("brewery-run-" + stamp + ".html");
      HtmlReportGenerator.generateReport(monitor, file);
      String opened = openInBrowser(file) ? " (opened in browser)" : "";
      chatHistory.add(ChatEntry.tool("Report for " + runs + " execution(s): " + file + opened));
    } catch (Exception e) {
      chatHistory.add(ChatEntry.error("Could not write report: " + e.getMessage()));
    }
  }

  private static boolean openInBrowser(Path file) {
    try {
      String os = System.getProperty("os.name", "").toLowerCase();
      List<String> cmd = os.contains("mac") ? List.of("open", file.toString())
          : os.contains("win") ? List.of("cmd", "/c", "start", "", file.toString())
          : List.of("xdg-open", file.toString());
      new ProcessBuilder(cmd).redirectErrorStream(true).start();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void handleTools() {
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.tool("No tools loaded. Use /mcp <name> <url> to connect to an MCP server."));
      return;
    }
    Map<String, List<String>> toolsByServer = mcpBridge.listToolNamesByServer();
    if (toolsByServer.isEmpty()) {
      chatHistory.add(ChatEntry.tool("Connected to MCP server(s) but no tools available."));
      return;
    }
    int total = toolsByServer.values().stream().mapToInt(List::size).sum();
    var sb = new StringBuilder("Available tools (" + total + "):\n");
    for (var entry : toolsByServer.entrySet()) {
      sb.append("  [").append(entry.getKey()).append("]\n");
      for (String tool : entry.getValue()) {
        sb.append("    - ").append(tool).append("\n");
      }
    }
    chatHistory.add(ChatEntry.tool(sb.toString().stripTrailing()));
  }

  private void handleModel(List<String> args) {
    if (args.isEmpty()) {
      chatHistory.add(ChatEntry.tool(renderModelHelp()));
      return;
    }

    String input = args.getFirst();
    ModelSwitcher.Resolved resolved;
    try {
      resolved = ModelSwitcher.resolve(input, activeProvider);
    } catch (IllegalArgumentException e) {
      chatHistory.add(ChatEntry.error(e.getMessage()));
      return;
    }

    // For Ollama, validate that the model is actually pulled locally.
    // Skip validation if discovery itself fails (server down) — let the chat
    // request bubble up the real error rather than blocking on a stale check.
    if (resolved.provider() == Provider.OLLAMA) {
      List<String> installed = ollamaDiscovery.list(appConfig.getOllamaBaseUrl());
      if (!installed.isEmpty() && !installed.contains(resolved.modelName())) {
        chatHistory.add(ChatEntry.error(
            "Ollama model '" + resolved.modelName() + "' is not installed locally.\n"
                + "Run: ollama pull " + resolved.modelName() + "\n"
                + "Installed: " + String.join(", ", installed)));
        return;
      }
    }

    activeProvider = resolved.provider();
    activeModel = resolved.modelName();
    rebuildAssistant();
    chatHistory.add(ChatEntry.tool("Switched to " + activeProvider + ":" + activeModel));
  }

  private String renderModelHelp() {
    var sb = new StringBuilder();
    sb.append("Current: ").append(activeProvider).append(":").append(activeModel).append("\n\n");
    sb.append("Usage:\n");
    sb.append("  /model <name>                — switch model on current provider\n");
    sb.append("  /model anthropic:<name>      — switch to Anthropic + model\n");
    sb.append("  /model ollama:<name>         — switch to Ollama + model\n\n");
    sb.append("Anthropic aliases: ").append(ModelSwitcher.anthropicAliases()).append("\n");

    if (appConfig != null) {
      List<String> ollamaModels = ollamaDiscovery.list(appConfig.getOllamaBaseUrl());
      if (!ollamaModels.isEmpty()) {
        sb.append("Ollama installed: ").append(String.join(", ", ollamaModels));
      } else {
        sb.append("Ollama: server unreachable at ").append(appConfig.getOllamaBaseUrl());
      }
    }
    return sb.toString();
  }

  private void handleThinking() {
    thinkingEnabled = !thinkingEnabled;
    rebuildAssistant();
    chatHistory.add(ChatEntry.tool("Extended thinking: " + (thinkingEnabled ? "ON" : "OFF")));
  }

  private void handleMcp(List<String> args) {
    if (mcpBridge == null) {
      mcpBridge = new McpBridge();
    }

    // /mcp (no args) → list connected servers
    if (args.isEmpty()) {
      var servers = mcpBridge.getConnectedServers();
      if (servers.isEmpty()) {
        chatHistory.add(ChatEntry.tool("No MCP servers connected.\nUsage: /mcp <name> <url> | /mcp disconnect [name]"));
        return;
      }
      var sb = new StringBuilder("Connected MCP servers:\n");
      for (var entry : servers.entrySet()) {
        sb.append("  ").append(entry.getKey()).append(" — ").append(entry.getValue()).append(" tools\n");
      }
      chatHistory.add(ChatEntry.tool(sb.toString().stripTrailing()));
      return;
    }

    // /mcp disconnect [name]
    if ("disconnect".equals(args.getFirst())) {
      String result;
      if (args.size() >= 2) {
        result = mcpBridge.disconnect(args.get(1));
      } else {
        result = mcpBridge.disconnectAll();
      }
      chatHistory.add(ChatEntry.tool(result));
      rebuildAssistant();
      return;
    }

    // /mcp <name> <url> → connect Streamable HTTP server
    if (args.size() >= 2) {
      String name = args.getFirst();
      String url = args.get(1);
      String result = mcpBridge.connectHttp(name, url);
      chatHistory.add(ChatEntry.tool(result));
      rebuildAssistant();
      return;
    }

    // /mcp <url> → single arg, treat as Streamable HTTP with auto-name
    String urlOrName = args.getFirst();
    if (urlOrName.startsWith("http")) {
      String result = mcpBridge.connectHttp("default", urlOrName);
      chatHistory.add(ChatEntry.tool(result));
      rebuildAssistant();
    } else {
      chatHistory.add(ChatEntry.error("Usage: /mcp <name> <url> | /mcp disconnect [name]"));
    }
  }

  private void handleUsage(List<String> args) {
    if (!args.isEmpty() && "reset".equalsIgnoreCase(args.getFirst())) {
      usageTracker.reset();
      chatHistory.add(ChatEntry.tool("Usage counters reset."));
      return;
    }
    var s = usageTracker.snapshot();
    var sb = new StringBuilder("Token usage (session totals):\n");
    sb.append(String.format("  Requests:        %d (errors: %d)%n", s.requests(), s.errors()));
    sb.append(String.format("  Input tokens:    %,d%n", s.inputTokens()));
    sb.append(String.format("  Output tokens:   %,d%n", s.outputTokens()));
    sb.append(String.format("  Total tokens:    %,d%n", s.totalTokens()));
    if (s.estimatedCostUsd() > 0) {
      sb.append(String.format("  Estimated cost:  $%.4f USD%n", s.estimatedCostUsd()));
    } else {
      sb.append("  Estimated cost:  $0.00 (no priced models, or local provider)\n");
    }
    sb.append(String.format("  Last latency:    %d ms%n", s.lastLatencyMs()));
    if (!s.lastModel().isEmpty()) {
      sb.append("  Last model:      ").append(s.lastModel()).append("\n");
    }
    sb.append("\nNote: tool-calling loops issue extra round-trips, so totals may exceed\n");
    sb.append("one bump per user message. Use /usage reset to clear.");
    chatHistory.add(ChatEntry.tool(sb.toString().stripTrailing()));
  }

  private void rebuildAssistant() {
    if (appConfig == null || conversationFactory == null) return;
    try {
      streamBridge = conversationFactory.create(
          mcpBridge, activeProvider, activeModel, thinkingEnabled, agenticEnabled);
    } catch (RuntimeException e) {
      chatHistory.add(ChatEntry.error("Could not rebuild the "
          + (agenticEnabled ? "agentic" : "single") + " assistant: " + e.getMessage()));
    }
  }

  private void handleTopicsShortcut() {
    if (mcpBridge == null || !mcpBridge.isConnected()) {
      chatHistory.add(ChatEntry.error("No MCP server connected. Use /mcp <url> first."));
      return;
    }
    // Send as AI request — the AI has the MCP tools to list topics.
    // currentResponse initialized lazily by the AgentEvent.Token handler.
    textarea.blur();
    isStreaming = true;
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
          sb.append(ToolResultView.render(toolEntry.toolName(), toolEntry.content(), innerContentWidth()));
        } else {
          sb.append(ToolResultView.renderCollapsed(toolEntry.toolName(), toolEntry.content(), innerContentWidth()));
        }
      } else {
        sb.append("  ").append(Theme.TOOL_BADGE.render("System"));
        sb.append("  ").append(Theme.TOOL.render(toolEntry.content()));
      }
      sb.append("\n\n");
    }

    // Show spinner during tool execution (prefixed with the sub-agent name once
    // the agentic system is in place; the single assistant stays unprefixed)
    if (isToolExecuting && currentToolName != null) {
      String who = (currentAgentName == null || SINGLE_ASSISTANT.equals(currentAgentName))
          ? "" : currentAgentName + " → ";
      sb.append("  ").append(spinner.view())
          .append(Theme.SPINNER_TEXT.render(" " + who + "Calling " + currentToolName + "()..."))
          .append("\n\n");
    }
    // Show partial response while streaming (with live markdown rendering)
    if (isStreaming && currentResponse != null && !currentResponse.isEmpty()) {
      sb.append("  ").append(Theme.AGENT_BADGE.render("Agent")).append("\n");
      String rendered = MarkdownRenderer.render(currentResponse.toString(), innerContentWidth());
      String[] lines = rendered.split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        sb.append("  ").append(lines[i]);
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

  /**
   * Plain-text notice shown when the terminal is below the minimum supported
   * size. Avoids rendering chrome that would overflow or clip in tiny windows.
   */
  private String renderTooSmallNotice() {
    String line1 = String.format("Terminal too small (%dx%d).", width, height);
    String line2 = String.format("Resize to at least %dx%d to use kafka-agent.",
        MIN_SUPPORTED_WIDTH, MIN_SUPPORTED_HEIGHT);
    String line3 = "Press Ctrl+C to quit.";
    return "\n" + line1 + "\n" + line2 + "\n\n" + line3 + "\n";
  }

  /**
   * Render the welcome banner. {@code availableWidth} is the width budget the
   * caller has measured ({@link #innerContentWidth()}); the box is sized to
   * fit that budget, capped at {@link #WELCOME_BOX_MAX_WIDTH} for readability
   * on wide terminals.
   */
  private static String renderWelcome(String appName, int availableWidth) {
    String title = Theme.WELCOME_TITLE.render(appName);
    String subtitle = Theme.WELCOME_TEXT.render("AI-powered Kafka & Flink assistant");
    String hint1 = Theme.WELCOME_TEXT.render("Type a message to begin");
    String hint2 = Theme.WELCOME_TEXT.render("/help for available commands");
    String hint3 = Theme.WELCOME_TEXT.render("Ctrl+O to show/hide tool output");

    String content = title + "\n\n" + subtitle + "\n\n" + hint1 + "\n" + hint2 + "\n" + hint3;
    int boxWidth = Math.max(20, Math.min(availableWidth, WELCOME_BOX_MAX_WIDTH));
    return Theme.WELCOME_BOX.width(boxWidth).margin(1, 0, 1, 2).render(content);
  }
}
