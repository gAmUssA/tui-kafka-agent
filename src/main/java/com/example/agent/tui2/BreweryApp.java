package com.example.agent.tui2;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.fill;
import static dev.tamboui.toolkit.Toolkit.length;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.percent;
import static dev.tamboui.toolkit.Toolkit.richTextArea;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.spinner;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.ConversationBridge;
import com.example.agent.agent.ConversationFactory;
import com.example.agent.agent.ModelSwitcher;
import com.example.agent.agent.Provider;
import com.example.agent.agent.UsageTracker;
import com.example.agent.config.AppConfig;
import com.example.agent.tools.McpBridge;
import com.example.agent.tui.CommandParser;
import com.example.agent.tui.CommandRegistry;
import com.example.agent.tui.SlashCommand;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Row;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.spinner.SpinnerStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Three-panel TamboUI front end:
 * <pre>
 *  header:  app │ provider:model │ mode │ MCP │ status
 *  ┌ Chat (what the user and the presenter say) ┐┌ Activity (hand-offs, tool calls) ┐
 *  └────────────────────────────────────────────┘└──────────────────────────────────┘
 *  ┌ Prompt ───────────────────────────────────────────────────────────────────────┐
 *  usage line
 * </pre>
 * Enter on an Activity row opens a detail overlay with the tool arguments and
 * result; Esc closes it. Tab cycles Prompt → Chat → Activity. Ctrl+C quits.
 * <p>
 * All state lives in fields and is mutated on the render thread only; agent
 * events arrive on LangChain4j threads and are marshalled with
 * {@code runner().runOnRenderThread(...)}.
 */
public final class BreweryApp extends ToolkitApp {

    private static final String CHAT_ID = "chat";
    private static final String ACTIVITY_ID = "activity";
    private static final String PROMPT_ID = "prompt";
    private static final String DETAIL_ID = "detail";
    private static final String SINGLE_ASSISTANT = "assistant";

    // Projector-safe palette (no dim/gray foregrounds)
    private static final Color USER = Color.CYAN;
    private static final Color AGENT = Color.WHITE;
    private static final Color SYSTEM = Color.YELLOW;
    private static final Color ERROR = Color.RED;
    private static final Color HANDOFF = Color.MAGENTA;
    private static final Color DONE = Color.GREEN;
    private static final Color TOOL = Color.BLUE;
    private static final Color FOCUSED = Color.GREEN;
    private static final Color UNFOCUSED = Color.WHITE;

    private record ChatLine(String text, Color color, boolean bold) { }

    // --- wiring ---------------------------------------------------------
    private final AppConfig config;
    private final ConversationFactory factory;
    private final UsageTracker usage;
    private McpBridge mcpBridge;
    private ConversationBridge bridge;
    private boolean agenticEnabled;
    private Provider activeProvider;
    private String activeModel;
    private boolean thinkingEnabled;

    // --- persistent stateful elements (never rebuilt in render) -----------
    private final TextInputState prompt = new TextInputState();
    private final ListElement<?> chatList = list()
            .displayOnly().stickyScroll().scrollbar().id(CHAT_ID).focusable();
    // Selection mode (not stickyScroll): in sticky mode Up/Down scroll instead of
    // moving the selection, so Enter could never target a row. We keep the newest
    // row selected while a turn runs (see follow()), which gives the same
    // auto-follow behaviour, and the user can arrow away at any time.
    private final ListElement<?> activityList = list()
            .highlightSymbol("▶ ").autoScroll().scrollbar().id(ACTIVITY_ID).focusable();

    // --- model ------------------------------------------------------------
    private final List<ChatLine> chatLines = new ArrayList<>();
    private final List<Activity> activity = new ArrayList<>();
    private StringBuilder streaming;              // presenter text of the current turn
    private boolean busy;
    private String busyLabel = "";
    private final List<String> activeAgents = new ArrayList<>();
    private Activity detail;                      // open overlay, or null
    private boolean quitRequested;
    private final int chatWrap;
    private final int activityWrap;

    public BreweryApp(AppConfig config, McpBridge mcpBridge, boolean agentic) {
        this.config = config;
        this.mcpBridge = mcpBridge;
        this.agenticEnabled = agentic;
        this.activeProvider = config.getProvider();
        this.activeModel = (activeProvider == Provider.OLLAMA) ? config.getOllamaModel() : config.getAnthropicModel();
        this.chatWrap = config.getUiChatWrap();
        this.activityWrap = config.getUiActivityWrap();
        this.usage = new UsageTracker();
        // Events arrive on LangChain4j threads; hop onto the render thread. runner()
        // is null before run() and after stop, in which case events are dropped.
        AgentEvent.Sink sink = event -> {
            var r = runner();
            if (r != null) {
                r.runOnRenderThread(() -> onEvent(event));
            }
        };
        this.factory = new ConversationFactory(config, sink, usage);
        this.bridge = factory.create(mcpBridge, activeProvider, null, false, agenticEnabled);
    }

    public String modeLabel() {
        return bridge.modeLabel();
    }

    // ------------------------------------------------------------------
    // ToolkitApp lifecycle
    // ------------------------------------------------------------------

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder()
                .mouseCapture(true)          // wheel scroll + click-to-focus in both panes
                .bindings(BindingSets.standard().toBuilder()
                        .unbind(Actions.QUIT)                       // bare 'q' must not quit a demo
                        .bind(KeyTrigger.ctrl('c'), Actions.QUIT)
                        .build())
                .build();
    }

    @Override
    protected void onStart() {
        runner().focusManager().setFocus(PROMPT_ID);
        String name = config.getAppName();
        system(name + " — " + (agenticEnabled ? "multi-agent" : "single-agent") + " mode. "
                + "Type a message, /help for commands. Tab cycles panes; Enter on an activity row shows details.");
    }

    @Override
    protected void onStop() {
        if (mcpBridge != null) {
            mcpBridge.close();
        }
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    protected Element render() {
        if (quitRequested) {
            quit();
        }
        String focused = runner() != null ? runner().focusManager().focusedId() : PROMPT_ID;

        chatList.elements(chatRows());
        activityList.elements(activityRows());

        Element main = column(
                header().constraint(length(1)),
                row(
                        panel("Chat", chatList).rounded()
                                .borderColor(border(focused, CHAT_ID)).constraint(percent(64)),
                        panel("Activity", activityList).rounded()
                                .borderColor(border(focused, ACTIVITY_ID)).constraint(fill())
                ).constraint(fill()),
                panel("Prompt", textInput(prompt)
                        .placeholder(busy ? "working… (Enter is ignored until the turn ends)" : "Type a message… (/help)")
                        .onSubmit(this::submit)
                        .id(PROMPT_ID).focusable())
                        .rounded().borderColor(border(focused, PROMPT_ID)).constraint(length(3)),
                row(text(usageLine()).fg(SYSTEM)).constraint(length(1))
        );

        if (detail == null) {
            return main;
        }
        return stack(main, detailOverlay(focused));
    }

    private Row header() {
        int tools = (mcpBridge != null && mcpBridge.isConnected()) ? mcpBridge.getToolCount() : 0;
        String mode = bridge != null ? bridge.modeLabel() : "";
        var parts = new ArrayList<Element>();
        parts.add(text(" " + config.getAppName() + " ").bold().fg(Color.BLACK).bg(Color.CYAN));
        parts.add(text("  " + activeProvider + ":" + shortModel(activeModel)).fg(Color.CYAN));
        if (!mode.isEmpty()) {
            parts.add(text("  │ " + mode).fg(HANDOFF));
        }
        if (tools > 0) {
            parts.add(text("  │ MCP: " + tools + " tools").fg(Color.CYAN));
        }
        parts.add(spacer());
        if (busy) {
            parts.add(spinner(SpinnerStyle.DOTS, " " + busyLabel).fg(SYSTEM));
        }
        return row(parts.toArray(new Element[0]));
    }

    private Element detailOverlay(String focused) {
        String title = switch (detail.kind) {
            case TOOL_DONE, TOOL_RUNNING -> detail.title + "()  ·  " + detail.agent;
            default -> detail.label();
        };
        String body = (detail.detail == null || detail.detail.isBlank()) ? "(no detail)" : detail.detail;
        return dialog(title,
                richTextArea(body).wrapWord().scrollbar().id(DETAIL_ID).focusable(),
                text("Esc closes · PgUp/PgDn scroll").fg(SYSTEM))
                .width(Math.min(chatWrap + 10, 120)).length(30)
                .rounded().borderColor(SYSTEM)
                .onCancel(this::closeDetail)
                .onConfirm(this::closeDetail);
    }

    private StyledElement<?>[] chatRows() {
        var rows = new ArrayList<StyledElement<?>>();
        for (ChatLine line : chatLines) {
            var t = text(line.text()).fg(line.color());
            rows.add(line.bold() ? t.bold() : t);
        }
        if (streaming != null && !streaming.isEmpty()) {
            for (String l : Wrap.wrap(streaming.toString(), chatWrap)) {
                rows.add(text("  " + l).fg(AGENT));
            }
        }
        return rows.toArray(new StyledElement<?>[0]);
    }

    private StyledElement<?>[] activityRows() {
        var rows = new ArrayList<StyledElement<?>>();
        for (Activity a : activity) {
            String label = Wrap.oneLine(a.label(), activityWrap);
            var t = text(label);
            rows.add(switch (a.kind) {
                case AGENT_START -> t.fg(HANDOFF).bold();
                case AGENT_DONE -> t.fg(DONE);
                case TOOL_RUNNING, TOOL_DONE -> t.fg(TOOL);
                case ERROR -> t.fg(ERROR).bold();
                case NOTE -> t.fg(SYSTEM);
            });
        }
        return rows.toArray(new StyledElement<?>[0]);
    }

    private static Color border(String focused, String id) {
        return id.equals(focused) ? FOCUSED : UNFOCUSED;
    }

    private String usageLine() {
        var s = usage.snapshot();
        if (s.requests() == 0) {
            return "  tokens: — (no requests yet)";
        }
        String cost = s.estimatedCostUsd() > 0 ? String.format("  $%.4f", s.estimatedCostUsd()) : "  $0.00 (local)";
        return String.format("  tokens: %,d in · %,d out%s  last %d ms  %d req%s%s",
                s.inputTokens(), s.outputTokens(), cost, s.lastLatencyMs(), s.requests(),
                s.requests() == 1 ? "" : "s", s.errors() > 0 ? "  " + s.errors() + " err" : "");
    }

    private static String shortModel(String model) {
        if (model == null) return "?";
        if (model.contains("sonnet")) return "sonnet";
        if (model.contains("haiku")) return "haiku";
        if (model.contains("opus")) return "opus";
        return model;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    {
        // Enter on an activity row opens its detail; the list itself leaves Enter unhandled.
        activityList.onKeyEvent(e -> {
            if (e.isConfirm() && detail == null && !activity.isEmpty()) {
                int i = Math.max(0, Math.min(activityList.selected(), activity.size() - 1));
                openDetail(activity.get(i));
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });
    }

    private void openDetail(Activity a) {
        detail = a;
        runner().focusManager().setFocus(DETAIL_ID);
    }

    private void closeDetail() {
        detail = null;
        runner().focusManager().setFocus(ACTIVITY_ID);
    }

    private void submit() {
        String line = prompt.text().trim();
        prompt.clear();
        if (line.isEmpty()) {
            return;
        }
        Optional<SlashCommand> cmd = CommandParser.parse(line);
        if (cmd.isPresent()) {
            user(line);
            handleCommand(cmd.get());
            return;
        }
        if (busy) {
            system("A turn is still running — wait for it to finish (or /quit).");
            return;
        }
        if (bridge == null) {
            error("No conversation bridge configured.");
            return;
        }
        user(line);
        startTurn("thinking…");
        bridge.sendMessage(line);
    }

    // ------------------------------------------------------------------
    // Agent events (render thread)
    // ------------------------------------------------------------------

    private void onEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.AgentStarted(String agent) -> {
                activeAgents.add(agent);
                busyLabel = agent + " working…";
                if (!SINGLE_ASSISTANT.equals(agent)) {
                    addActivity(new Activity(Activity.Kind.AGENT_START, agent, agent, null));
                }
            }
            case AgentEvent.AgentFinished(String agent, Object output, long ms, String summary) -> {
                activeAgents.remove(agent);
                busyLabel = activeAgents.isEmpty() ? "finishing…" : activeAgents.getLast() + " working…";
                if (!SINGLE_ASSISTANT.equals(agent)) {
                    // Turn the "working…" row into the "done" row so the pane stays compact.
                    Activity start = lastRow(Activity.Kind.AGENT_START, agent);
                    Activity done = (start != null) ? start
                            : new Activity(Activity.Kind.AGENT_DONE, agent, agent, null);
                    done.kind = Activity.Kind.AGENT_DONE;
                    done.durationMs = ms;
                    done.summary = summary;
                    done.detail = output == null ? null : String.valueOf(output);
                    if (start == null) {
                        addActivity(done);
                    }
                }
            }
            case AgentEvent.ToolStarted(String agent, String tool, String args) -> {
                busyLabel = (agent == null || SINGLE_ASSISTANT.equals(agent) ? "" : agent + " → ") + tool + "()";
                addActivity(new Activity(Activity.Kind.TOOL_RUNNING, agent, tool, "args: " + args));
            }
            case AgentEvent.ToolFinished(String agent, String tool, String result) -> {
                Activity running = lastRow(Activity.Kind.TOOL_RUNNING, tool);
                Activity done = (running != null) ? running
                        : new Activity(Activity.Kind.TOOL_DONE, agent, tool, "");
                done.kind = Activity.Kind.TOOL_DONE;
                done.detail = (running != null ? running.detail + "\n---\n" : "") + result;
                if (running == null) {
                    addActivity(done);
                }
                busyLabel = activeAgents.isEmpty() ? "thinking…" : activeAgents.getLast() + " working…";
            }
            case AgentEvent.Token(String textChunk) -> {
                if (streaming == null) {
                    streaming = new StringBuilder();
                    chatLines.add(new ChatLine("Agent", AGENT, true));
                }
                streaming.append(textChunk);
            }
            case AgentEvent.Completed(String fullText) -> {
                if (streaming != null) {
                    commitStreaming();
                } else if (!fullText.isBlank()) {
                    chatLines.add(new ChatLine("Agent", AGENT, true));
                    for (String l : Wrap.wrap(fullText, chatWrap)) {
                        chatLines.add(new ChatLine("  " + l, AGENT, false));
                    }
                }
                chatLines.add(new ChatLine("", AGENT, false));
                endTurn();
            }
            case AgentEvent.Failed failed -> {
                if (streaming != null) {
                    commitStreaming();
                }
                String where = (failed.agent() == null || SINGLE_ASSISTANT.equals(failed.agent())) ? "" : failed.agent() + ": ";
                error(where + failed.message());
                addActivity(new Activity(Activity.Kind.ERROR, failed.agent(), failed.message(), stackOf(failed.error())));
                endTurn();
            }
        }
    }

    /** Append an activity row and keep the selection on the newest row while busy. */
    private void addActivity(Activity a) {
        activity.add(a);
        follow();
    }

    private void follow() {
        if (!activity.isEmpty() && (busy || activityList.selected() >= activity.size() - 2)) {
            activityList.selected(activity.size() - 1);
        }
    }

    private Activity lastRow(Activity.Kind kind, String key) {
        for (int i = activity.size() - 1; i >= 0; i--) {
            Activity a = activity.get(i);
            if (a.kind == kind && (key.equals(a.agent) || key.equals(a.title))) {
                return a;
            }
        }
        return null;
    }

    private void commitStreaming() {
        for (String l : Wrap.wrap(streaming.toString(), chatWrap)) {
            chatLines.add(new ChatLine("  " + l, AGENT, false));
        }
        streaming = null;
    }

    private void startTurn(String label) {
        busy = true;
        busyLabel = label;
        activeAgents.clear();
        addActivity(new Activity(Activity.Kind.NOTE, null, "turn " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), null));
    }

    private void endTurn() {
        busy = false;
        busyLabel = "";
        activeAgents.clear();
        runner().focusManager().setFocus(PROMPT_ID);
    }

    private static String stackOf(Throwable t) {
        var sb = new StringBuilder();
        for (Throwable c = t; c != null && sb.length() < 4000; c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Chat helpers
    // ------------------------------------------------------------------

    private void user(String text) {
        chatLines.add(new ChatLine("You", USER, true));
        for (String l : Wrap.wrap(text, chatWrap)) {
            chatLines.add(new ChatLine("  " + l, USER, false));
        }
        chatLines.add(new ChatLine("", USER, false));
    }

    private void system(String text) {
        for (String l : Wrap.wrap(text, chatWrap)) {
            chatLines.add(new ChatLine("  " + l, SYSTEM, false));
        }
        chatLines.add(new ChatLine("", SYSTEM, false));
    }

    private void error(String text) {
        chatLines.add(new ChatLine("Error", ERROR, true));
        for (String l : Wrap.wrap(text, chatWrap)) {
            chatLines.add(new ChatLine("  " + l, ERROR, false));
        }
        chatLines.add(new ChatLine("", ERROR, false));
    }

    // ------------------------------------------------------------------
    // Slash commands
    // ------------------------------------------------------------------

    private void handleCommand(SlashCommand cmd) {
        switch (cmd.name()) {
            case "help" -> {
                int w = CommandRegistry.ALL.stream().mapToInt(c -> c.displayName().length()).max().orElse(10);
                var sb = new StringBuilder("Commands\n");
                for (var c : CommandRegistry.ALL) {
                    sb.append(String.format("%-" + (w + 2) + "s %s%n", c.displayName(), c.description()));
                }
                sb.append("Keys: Tab cycles panes · Enter on an Activity row shows detail · Esc closes · PgUp/PgDn scroll · Ctrl+C quits");
                system(sb.toString());
            }
            case "clear" -> {
                chatLines.clear();
                activity.clear();
                if (bridge != null) bridge.reset();
                rebuild();
                system("Chat cleared.");
            }
            case "agentic" -> {
                if (cmd.args().isEmpty()) {
                    system("Agentic mode is " + (agenticEnabled ? "ON" : "OFF") + ". Usage: /agentic on | off");
                    return;
                }
                switch (cmd.args().getFirst().toLowerCase()) {
                    case "on" -> agenticEnabled = true;
                    case "off" -> agenticEnabled = false;
                    default -> { error("Usage: /agentic on | off"); return; }
                }
                rebuild();
                system("Agentic mode " + (agenticEnabled ? "ON" : "OFF") + " — using " + bridge.modeLabel() + " bridge.");
            }
            case "model" -> {
                if (cmd.args().isEmpty()) {
                    system("Current: " + activeProvider + ":" + activeModel + "\nUsage: /model <name> | anthropic:<name> | ollama:<name>\nAliases: " + ModelSwitcher.anthropicAliases());
                    return;
                }
                try {
                    var r = ModelSwitcher.resolve(cmd.args().getFirst(), activeProvider);
                    activeProvider = r.provider();
                    activeModel = r.modelName();
                    rebuild();
                    system("Switched to " + activeProvider + ":" + activeModel);
                } catch (IllegalArgumentException e) {
                    error(e.getMessage());
                }
            }
            case "thinking" -> {
                thinkingEnabled = !thinkingEnabled;
                rebuild();
                system("Extended thinking: " + (thinkingEnabled ? "ON" : "OFF"));
            }
            case "tools" -> {
                if (mcpBridge == null || !mcpBridge.isConnected()) {
                    system("No MCP servers connected.");
                    return;
                }
                var sb = new StringBuilder();
                for (Map.Entry<String, List<String>> e : mcpBridge.listToolNamesByServer().entrySet()) {
                    sb.append('[').append(e.getKey()).append("] ").append(String.join(", ", e.getValue())).append('\n');
                }
                system(sb.toString());
            }
            case "mcp" -> {
                if (mcpBridge == null) mcpBridge = new McpBridge();
                if (cmd.args().isEmpty()) {
                    var servers = mcpBridge.getConnectedServers();
                    system(servers.isEmpty() ? "No MCP servers connected. Usage: /mcp <name> <url> | /mcp disconnect [name]"
                            : "Connected: " + servers);
                } else if ("disconnect".equals(cmd.args().getFirst())) {
                    system(cmd.args().size() > 1 ? mcpBridge.disconnect(cmd.args().get(1)) : mcpBridge.disconnectAll());
                    rebuild();
                } else if (cmd.args().size() >= 2) {
                    system(mcpBridge.connectHttp(cmd.args().get(0), cmd.args().get(1)));
                    rebuild();
                } else if (cmd.args().getFirst().startsWith("http")) {
                    system(mcpBridge.connectHttp("default", cmd.args().getFirst()));
                    rebuild();
                } else {
                    error("Usage: /mcp <name> <url> | /mcp disconnect [name]");
                }
            }
            case "usage" -> {
                if (!cmd.args().isEmpty() && "reset".equalsIgnoreCase(cmd.args().getFirst())) {
                    usage.reset();
                    system("Usage counters reset.");
                } else {
                    system(usageLine().trim());
                }
            }
            case "report" -> report();
            case "topics" -> ask("List all my Kafka topics.");
            case "sql" -> {
                if (cmd.args().isEmpty()) { error("Usage: /sql <flink-sql-query>"); return; }
                ask("Execute this Flink SQL query: " + String.join(" ", cmd.args()));
            }
            case "setup" -> demo(config.getDemoSetupPrompt(), "setup");
            case "reset" -> demo(config.getDemoResetPrompt(), "reset");
            case "quit" -> quitRequested = true;
            default -> error("Unknown command: /" + cmd.name() + ". Type /help.");
        }
    }

    private void demo(String promptText, String name) {
        if (promptText == null) {
            error("/" + name + " is not configured. Add demo." + name + " to your config YAML.");
            return;
        }
        ask(promptText);
    }

    private void ask(String text) {
        if (busy) {
            system("A turn is still running.");
            return;
        }
        startTurn("thinking…");
        bridge.sendMessage(text);
    }

    private void rebuild() {
        try {
            bridge = factory.create(mcpBridge, activeProvider, activeModel, thinkingEnabled, agenticEnabled);
        } catch (RuntimeException e) {
            error("Could not rebuild the assistant: " + e.getMessage());
        }
    }

    private void report() {
        var monitor = factory.monitor();
        int runs = monitor.successfulExecutions().size() + monitor.failedExecutions().size();
        if (runs == 0) {
            system("No agentic executions recorded yet.");
            return;
        }
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "kafka-agent-reports");
            Files.createDirectories(dir);
            Path file = dir.resolve("brewery-run-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html");
            HtmlReportGenerator.generateReport(monitor, file);
            boolean opened = false;
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                List<String> open = os.contains("mac") ? List.of("open", file.toString())
                        : os.contains("win") ? List.of("cmd", "/c", "start", "", file.toString())
                        : List.of("xdg-open", file.toString());
                new ProcessBuilder(open).redirectErrorStream(true).start();
                opened = true;
            } catch (Exception ignored) {
            }
            system("Report for " + runs + " execution(s): " + file + (opened ? " (opened)" : ""));
        } catch (Exception e) {
            error("Could not write report: " + e.getMessage());
        }
    }
}
