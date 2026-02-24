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

    private static final int INPUT_HEIGHT = 3;
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
        viewport.setContent("  Welcome to kafka-agent. Type a message to begin. /help for commands.\n");
    }

    public void setStreamBridge(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public void setAppConfig(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.activeModel = appConfig.getAnthropicModel();
    }

    @Override
    public Command init() {
        return textarea.init();
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        // Handle tool executing
        if (msg instanceof ToolExecutingMessage toolMsg) {
            isToolExecuting = true;
            currentToolName = toolMsg.toolName();
            refreshViewport();
            return UpdateResult.from(this, spinner.init());
        }

        // Handle tool completion
        if (msg instanceof ToolCompleteMessage toolMsg) {
            isToolExecuting = false;
            chatHistory.add(ChatEntry.tool(toolMsg.toolName(), toolMsg.result()));
            currentToolName = null;
            refreshViewport();
            return UpdateResult.from(this);
        }

        // Handle streaming tokens
        if (msg instanceof StreamTokenMessage tokenMsg) {
            if (currentResponse == null) {
                currentResponse = new StringBuilder();
            }
            currentResponse.append(tokenMsg.token());
            refreshViewport();
            return UpdateResult.from(this);
        }

        // Handle stream completion
        if (msg instanceof StreamCompleteMessage completeMsg) {
            isStreaming = false;
            String response = completeMsg.fullResponse();
            if (!response.isBlank()) {
                chatHistory.add(ChatEntry.agent(response));
            }
            currentResponse = null;
            textarea.focus();
            refreshViewport();
            return UpdateResult.from(this);
        }

        // Handle errors
        if (msg instanceof ErrorMessage errorMsg) {
            isStreaming = false;
            isToolExecuting = false;
            currentToolName = null;
            currentResponse = null;
            chatHistory.add(ChatEntry.error(errorMsg.error()));
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
        String header = HeaderView.render(activeModel, toolCount, isStreaming, isToolExecuting, width);
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
            default -> chatHistory.add(ChatEntry.error(
                    "Unknown command: /" + cmd.name() + ". Type /help for available commands."));
        }
    }

    private void handleHelp() {
        String help = """
                Available commands:
                  /help              Show this help message
                  /clear             Clear chat history
                  /tools             List available tools
                  /model <name>      Switch model (sonnet, haiku, opus)
                  /thinking          Toggle extended thinking mode
                  /mcp <url>         Connect to MCP server
                  /topics            List Kafka topics (via AI)
                  /sql <query>       Run Flink SQL (via AI)""";
        chatHistory.add(ChatEntry.tool(help));
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

    private void refreshViewport() {
        var sb = new StringBuilder();
        sb.append(ChatView.render(chatHistory, width));

        // Show spinner during tool execution
        if (isToolExecuting && currentToolName != null) {
            sb.append("  ").append(spinner.view())
              .append(Theme.TOOL.render(" Calling " + currentToolName + "()..."))
              .append("\n\n");
        }
        // Show partial response while streaming
        if (isStreaming && currentResponse != null && !currentResponse.isEmpty()) {
            sb.append("  ").append(Theme.AGENT_LABEL.render("Agent: "))
              .append(Theme.AGENT.render(currentResponse.toString()))
              .append("▌\n\n");
        }
        viewport.setContent(sb.toString());
        viewport.gotoBottom();
    }
}
