# Brewmaster Demo — Migration to langchain4j-agentic + TamboUI

**Status:** approved 2026-09-07 (decisions in §7), phases 0–3 complete, phase 4 (demo re-tune) next
**Scope:** (1) split the single "do-everything" assistant into cooperating agents with `langchain4j-agentic`, separating the Confluent Cloud / Flink platform concern from the brewmaster domain concern; (2) replace the tui4j (Bubble Tea port) UI with TamboUI.

---

## 1. Where we are

| Layer | Today | Notes |
|---|---|---|
| LLM framework | LangChain4j **1.11.0**, `langchain4j-mcp:1.11.0-beta19` | One `AiServices` interface (`AgentAssistant`) returning `TokenStream`, one 50-message memory window, one giant system prompt (`docs/brewmaster-config.yaml`) that mixes brewing knowledge, Avro schemas, and Flink SQL protocol |
| Tools | `McpBridge` → single `McpToolProvider` over all connected servers | Every tool exposed to the one agent; no scoping |
| TUI | tui4j **0.3.3** (`com.williamcallahan:tui4j`), JLine FFM 3.26.1 | ~1,700 LOC in `tui/`: Elm-style `AgentModel` (742 LOC), custom `MarkdownRenderer` (214 LOC), `Theme` (264 LOC, lipgloss), header/chat/tool views, slash-command popup |
| Bridge | `StreamBridge` maps `TokenStream` callbacks → `Program.send(Message)` | The only seam between agent and UI |
| Build | Gradle KTS, Java 21 toolchain, shadow fat JAR, GraalVM native + jlink targets | No `-parameters` compiler flag (required by agentic `@V` binding) |

Demo: 6 acts (setup → fill → monitor → sabotage → diagnose → evolve), all driven by conversation through the Confluent MCP server (topics, tags, Schema Registry, produce/consume, Flink statements).

---

## 2. Research findings (verified 2026-09-07)

### 2.1 LangChain4j — current versions (Maven Central)

| Artifact | Version | Status |
|---|---|---|
| `langchain4j`, `langchain4j-bom`, `langchain4j-anthropic`, `langchain4j-ollama` | **1.20.0** | GA, published 2026-09-04 |
| `langchain4j-agentic`, `langchain4j-agentic-a2a`, `langchain4j-agentic-patterns`, `langchain4j-mcp` | **1.20.0-beta30** | Beta — **not managed by the BOM**, pin explicitly, keep all betas on the same number |

Our project is 9 minor releases behind (1.11 → 1.20). The project's own Tessl tile `gamussa/langchain4j-agentic@0.2.1` is pinned at 1.15.0 / beta25; its `check_versions.sh` confirms drift to 1.20.0 / beta30. Both tiles (`gamussa/langchain4j-agentic`, `jbaruch/tamboui`) are now installed in `.tessl/` and their rules are active.

### 2.2 langchain4j-agentic — what the module gives us

* **`AgenticServices`** is the single factory: `agentBuilder(Class)`, `sequenceBuilder`, `loopBuilder`, `parallelBuilder`, `parallelMapperBuilder`, `conditionalBuilder`, `supervisorBuilder`, `plannerBuilder` (custom `Planner`), `humanInTheLoopBuilder`, `a2aBuilder`.
* An agent is an interface with one `@Agent`-annotated method; it is an `AiService` plus orchestration. Tools use the normal `@Tool`; MCP goes through **`.toolProviders(...)`** (not `.tools`). `.maxSequentialToolsInvocations(n)` is mandatory on tool-using agents.
* **`AgenticScope`** is the shared blackboard: an agent writes to `outputKey`, downstream agents read via `@V("key")`. A scope is created per top-level invocation.
* **Supervisor**: `supervisorBuilder().chatModel(planner).subAgents(...).responseStrategy(LAST|SUMMARY|SCORED).contextGenerationStrategy(CHAT_MEMORY|SUMMARIZATION|CHAT_MEMORY_AND_SUMMARIZATION).supervisorContext("policies…")`. Sub-agents are fixed at build time. Supervisor is LLM-planned = non-deterministic.
* **Streaming rule (critical for our TUI):** an agent returning `TokenStream` only propagates its stream to the caller when it is the **last** agent invoked in the system. Every earlier agent is buffered.
* **Observability**: `AgentListener` (`beforeAgentInvocation`, `afterAgentInvocation`, `onAgentInvocationError`, `beforeToolExecution`, `afterToolExecution`, `afterAgenticScopeCreated`…) registered with `.listener(...)`; set `inheritedBySubagents()=true` to see the whole tree. `AgentMonitor` + `HtmlReportGenerator.generateReport(monitor, path)` produce a topology/timeline HTML — the framework's best visual moment.
* **Error handling**: `.errorHandler(ctx -> ErrorRecoveryResult.retry() | .result(x) | .throwException())`.
* **Dynamic model per invocation**: `.chatModel(scope -> …)` — lets `/model` switching stay cheap.
* **1.18–1.20 additions** (release notes): human-in-the-loop suspend/resume, BDI pattern, tool action compensation, P2P planner termination, **non-blocking/reactive agent support and concurrent tool execution (1.20.0)**. Anthropic: mid-conversation system messages, Message Batches, cache diagnostics (beta). MCP: 2026-07-28 spec compliance, `_meta` on tool results, no more Jackson types on the public API.
* **Build gotchas**: compile with `-parameters` or every `@V` binds `null` silently; pin betas outside the BOM; `outputKey` is canonical (`outputName` is the stale name); parallel-mapper sub-agents cannot have `ChatMemory`.
* **API details confirmed against the 1.20 apidocs** (differ from the 1.15-era tile text): `AgentBuilder` has `toolProvider(ToolProvider)` / `toolProviders(...)`, `context(Function<AgenticScope,String>)` and `summarizedContext(String... agents)` for cross-agent context, `maxToolCallingRoundTrips(int)` (`maxSequentialToolsInvocations` is deprecated), `executeToolsConcurrently()`, `compensateOnError`. Supervisor builder has a typed overload `supervisorBuilder(Class)`, `chatMemoryProvider`, `maxAgentsInvocations(int)`, `requestGenerator`, `output(...)`, and **no `streamingChatModel`**. `AgentListener` tool hooks are named `beforeAgentToolExecution` / `afterAgentToolExecution` in the apidocs (the tutorial text says `beforeToolExecution`) — let the compiler decide. New module `langchain4j-agentic-mcp` (`McpAgent.builder(client).toolName(...).inputKeys(...).outputKey(...)`) wraps one MCP tool as a deterministic non-LLM agent — ideal for the `SqlSubmitter` step. A known NPE with streaming + listener + tools (issue #4795, beta22) was fixed in PR #4843.

### 2.2a ⚠ Hard breaker: SSE `HttpMcpTransport` was removed in 1.19.0

Verified by listing `langchain4j-mcp/.../client/transport/http` on the GitHub tags: `HttpMcpTransport.java` exists at 1.18.0 and is gone at 1.19.0 and 1.20.0; only `StreamableHttpMcpTransport` (+ `SseSubscriber`) remain. `McpBridge.createTransport()` (`tools/McpBridge.java:197`) builds the default transport with `HttpMcpTransport.builder().sseUrl(...)`, so the project will not compile on 1.19+ until that becomes `StreamableHttpMcpTransport.builder().url(...)`. Consequence: **the Confluent MCP server must expose a Streamable HTTP endpoint** (`/mcp`, POST). Legacy `/sse`-only servers are no longer reachable from the 1.19+ client. Transports in 1.20: stdio, Streamable HTTP, WebSocket, Docker (`langchain4j-mcp-docker`). Also in 1.20 the MCP module stopped exposing Jackson types (we don't touch them) and 1.16 made inherited `@Tool` methods discoverable (we have none).

### 2.3 TamboUI — current state

| Fact | Value |
|---|---|
| Coordinates | `dev.tamboui:tamboui-bom`, `tamboui-toolkit`, `tamboui-jline3-backend`, `tamboui-markdown` (+ `tamboui-toolkit-markdown`), `tamboui-css`, `tamboui-widgets`, `tamboui-tui` |
| Latest release | **0.4.0 on Maven Central** (0.5.0-SNAPSHOT on the snapshot repo). The tile's scaffold skill still says "snapshots only" — outdated; we can use the release. |
| Origin / license | Java port of Ratatui + Bubble Tea ideas, MIT, libraries are Java 8 source, demos Java 21 |
| Programming model | **Retained-mode declarative DSL** (`ToolkitApp.render()` returns an `Element` tree each frame) on top of an immediate-mode core. Not Elm: no `update(Message)` loop — state lives in your fields and in `*State` objects (`TextInputState`, `TextAreaState`, `ListState`). |
| Threading | Dedicated render thread. Background threads must call `runner().runOnRenderThread(Runnable)` (replaces `Program.send`). Scheduler callbacks (`scheduleRepeating`) are *not* on the render thread. A queued runnable does **not** trigger a repaint by itself — the next `TickEvent` (40 ms default) does, so never call `noTick()` in the chat app. |
| Elements we need | `panel`, `row`, `column`, `text`, `richText`, `richTextArea`, `markupText`, `list` (sticky scroll, scrollbar), `textInput` / `textArea`, `spinner(SpinnerStyle,…)`, `markdown(...)` (from `tamboui-toolkit-markdown`, **streaming-safe**: `PartialMarkdownSanitizer` handles token-by-token LLM output), `stack`/`dialog` (for the slash-command popup overlay), `scrollable` |
| Events | `.onKeyEvent(KeyEventHandler)` on any element returning `EventResult.HANDLED/UNHANDLED`; `KeyEvent` has `isCtrlC()`, `isEscape()`, `isTab()`, `isPageUp()/isPageDown()`, `hasCtrl()`, `isChar(c)`, `code()`; focus chain via `.id("x").focusable()`, Tab/Shift+Tab built in. Routing order: focused element → its `onKeyEvent` lambda → global handlers (`runner().eventRouter().addGlobalHandler`) → unfocused elements. **Default bindings quit on bare `q`** when a non-input pane has focus — rebind with `BindingSets.standard().toBuilder().unbind(Actions.QUIT).bind(KeyTrigger.ctrl('c'), Actions.QUIT)` in `configure()` |
| Config | `configure()` → `TuiConfig.builder().mouseCapture(true).alternateScreen(true)…` |
| Styling | Fluent modifiers (`.cyan().bold()`), or a **TCSS stylesheet** via `tamboui-css` with child selectors (`-cursor`, `-placeholder`, `-item`, `-thumb`…) — the `Theme.java` lipgloss constants become a `.tcss` file |
| Constraints | Needs a real TTY (Gradle `run` task won't work — our `./kafka-agent` launcher already does the right thing); GraalVM native-image support exists upstream for demos, but our `nativeCompile` target must be re-validated |

---

## 3. Target architecture

### 3.1 Separation of concerns

Today one prompt holds three personalities. The split:

```
┌──────────────────────────────────────────────────────────────────┐
│ TUI (TamboUI ToolkitApp)                                         │
│  chat list · streaming markdown · agent/tool trace · input       │
└───────────────▲───────────────────────────────┬──────────────────┘
                │ AgentEvent (sealed)           │ user turn
                │ runOnRenderThread             ▼
┌───────────────┴──────────────────────────────────────────────────┐
│ AgentOrchestrator  (langchain4j-agentic)                         │
│                                                                  │
│   BrewerySystem = sequence( BrewOpsSupervisor , Presenter* )     │
│                                                                  │
│   BrewOpsSupervisor (supervisorBuilder, Anthropic Sonnet)        │
│     supervisorContext = demo policies (Avro-first, DESCRIBE      │
│                         before SQL, style thresholds…)           │
│     ├─ BrewmasterAgent      domain expert, NO platform tools     │
│     │    generates typed SensorBatch / Diagnosis / AlertRule     │
│     ├─ ConfluentAgent       Kafka + Schema Registry + tags       │
│     │    MCP tools: topics, tags, schemas, produce, consume      │
│     └─ FlinkAgent           = loop( SqlAuthor → SqlCritic )      │
│          MCP tools: flink statements, list-flink-tables/DESCRIBE │
│          exits when CriticResult<FlinkStatement>.successful      │
│                                                                  │
│   * Presenter is the last agent → its TokenStream reaches the UI │
│   AgentListener(inheritedBySubagents=true) → AgentEvent bus      │
│   AgentMonitor → /report (HtmlReportGenerator)                   │
└──────────────────────────────────────────────────────────────────┘
```

**Why this shape**

* **Brewmaster = knowledge, Confluent/Flink = execution.** The brewmaster never touches MCP. Its outputs are *typed Java records* (`SensorBatch`, `AlertRule` with per-style thresholds, `Diagnosis`), parsed by LangChain4j structured output. The platform agents consume those records. This is the "domain-model the object that flows between agents" pattern from the `gamussa/langchain4j-agentic` tile — compile-checked handoffs instead of prose.
* **Two platform agents, not one.** Kafka/Schema Registry work is deterministic CRUD; Flink SQL authoring is the one place the demo needs a verify-and-fix loop (Act 3 today relies on "the agent self-corrects"). Giving Flink its own `loopBuilder` with a typed critic makes that moment reliable and narratable ("→ author → critic: FAIL, `$rowtime` not quoted → author → critic: PASS → submit"). If you want the minimum, merge ConfluentAgent and FlinkAgent into one `ConfluentCloudAgent`; the brewmaster boundary is the important one.
* **Per-agent tool scoping.** `McpBridge` builds one `McpToolProvider` per agent over the *same* MCP client(s) using `McpToolProvider.builder().mcpClients(...).filterToolNames("create-topics", …)` or the `filter((client, tool) -> …)` predicate (both confirmed on the current `McpToolProvider.Builder`). Smaller tool lists = better tool selection and smaller cached prompts. The MCP client also gains `protocolVersion("2026-07-28")` auto-detection and a `StreamableHttpMcpTransport` alongside the still-deprecated SSE `HttpMcpTransport`.
* **Supervisor for routing, workflows inside.** Each user turn is open-ended ("did the alerts fire?"), so LLM planning at the top is justified. Inside each specialist the control flow is deterministic (the tile's default guidance). `supervisorContext` carries the demo policies that currently live in the mega system prompt.
* **Streaming preserved by design.** Because only the last agent streams (and the supervisor builder has no streaming model at all), we add a lightweight **Presenter** agent (streaming model, no tools) as the final step of a `sequenceBuilder`. Lower-risk fallback if the spike disappoints: keep today's streaming `AiServices` assistant on top and expose each specialist workflow as a `@Tool` it can call — tokens stream exactly as now and sub-agent progress still arrives via `AgentListener`. It turns the supervisor's result plus scope state into the audience-facing narrative, token by token. Tool calls and agent handoffs are surfaced live through `AgentListener` events, so the UI is never silent while specialists work. Trade-off: the Flink SQL text itself is no longer typed live by the model; it appears when `SqlAuthor` finishes (shown from `beforeToolExecution` arguments) and the Presenter streams the explanation. Alternative if you insist on streaming SQL: make FlinkAgent's `SqlAuthor` a streaming agent and place it last for Act 3 only — not worth the complexity.
* **Cross-turn state.** An `AgenticScope` is ephemeral per invocation *unless* the root agent method takes a `@MemoryId` parameter — then the scope is kept in a registry keyed by that id and reused across calls (documented in the agents tutorial; the root interface can extend `AgenticScopeAccess`, and `evictAgenticScope(memoryId)` frees it — this is what `/clear` will call). Our `BrewerySystem.chat(@MemoryId String session, @V("request") String request)` therefore keeps `brewPlan`, `statement`, registered-schema facts etc. alive for the whole demo. A tiny `BrewerySessionState` summary string is still refreshed into `supervisorContext` each turn so the planner sees "topics exist, schema v1 registered" without reading raw scope keys.
* **Provider switching stays.** `ChatModelFactory` already returns a `StreamingChatModel`; agentic builders accept `.chatModel(ChatModel)` for non-streaming agents and `.streamingChatModel(...)` for the Presenter. `/model` and `/thinking` rebuild the system (cheap; builders are just wiring). Ollama small models are a poor supervisor planner — pin the planner to Anthropic and let `/model ollama:…` only swap the worker models.
* **Usage tracking stays.** `UsageTracker` is a `ChatModelListener` on the model; register it on every model instance. The `/usage` panel gains a per-agent breakdown from `AgentMonitor`.

### 3.2 Agent interfaces (sketch)

```java
public interface BrewmasterAgent {
    @SystemMessage(fromResource = "prompts/brewmaster.txt")   // brewing knowledge only
    @UserMessage("{{request}}\n\nCurrent brewery state: {{breweryState}}")
    @Agent(name = "brewmaster", outputKey = "brewPlan",
           description = "Craft-brewing expert. Produces sensor batches, style thresholds, and diagnoses. Never touches Kafka or Flink.")
    BrewPlan advise(@V("request") String request, @V("breweryState") String breweryState);
}

public interface ConfluentAgent {
    @SystemMessage(fromResource = "prompts/confluent.txt")    // Avro-first, schema rules, tags
    @UserMessage("{{request}}\n\nBrewmaster plan (may be empty): {{brewPlan}}")
    @Agent(name = "confluent", outputKey = "platformResult",
           description = "Operates Kafka topics, tags, Schema Registry, produce/consume on Confluent Cloud.")
    String operate(@V("request") String request, @V("brewPlan") BrewPlan brewPlan);
}

public interface SqlAuthor {
    @SystemMessage(fromResource = "prompts/flink-author.txt") // DESCRIBE first, window TVF, `$rowtime`
    @Agent(name = "sqlAuthor", outputKey = "statement")
    FlinkStatement write(@V("request") String request, @V("brewPlan") BrewPlan plan,
                         @V("feedback") String feedback);
}

public interface SqlCritic {
    @Agent(name = "sqlCritic", outputKey = "criticResult")
    CriticResult<FlinkStatement> verify(@V("statement") FlinkStatement s);   // has DESCRIBE tools
}

public interface Presenter {
    @Agent(name = "presenter", outputKey = "answer")
    TokenStream present(@V("request") String request, @V("platformResult") String platformResult,
                        @V("brewPlan") BrewPlan plan, @V("statement") FlinkStatement statement);
}
```

Wiring:

```java
var flinkLoop = AgenticServices.loopBuilder()
        .subAgents(sqlAuthor, sqlCritic)
        .maxIterations(3)
        .exitCondition(s -> s.readState("criticResult", CriticResult.pending()).successful())
        .build();
// deterministic submit step: one MCP tool wrapped as a non-LLM agent (langchain4j-agentic-mcp)
var sqlSubmitter = McpAgent.builder(confluentMcpClient)
        .toolName("create-flink-statement").inputKeys("statement").outputKey("platformResult").build();
var flinkAgent = AgenticServices.sequenceBuilder()
        .subAgents(flinkLoop, sqlSubmitter)
        .outputKey("platformResult").build();

var supervisor = AgenticServices.supervisorBuilder()
        .chatModel(plannerModel)
        .subAgents(brewmaster, confluent, flinkAgent)
        .supervisorContext(policies + "\nBrewery state: " + state.summary())
        .responseStrategy(SupervisorResponseStrategy.SUMMARY)
        .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
        .listener(uiListener).listener(monitor)
        .build();

public interface BrewerySystem extends AgenticScopeAccess {
    @Agent
    TokenStream chat(@MemoryId String session, @V("request") String request); // @MemoryId ⇒ persistent scope
}

BrewerySystem system = AgenticServices.sequenceBuilder(BrewerySystem.class)
        .subAgents(supervisor, presenter)              // presenter LAST → streams
        .outputKey("answer").build();
```

Spike check (phase 2, day 1): confirm on beta30 that a composed system's root method may return `TokenStream` when its last sub-agent streams. If it must return `String`, keep the Presenter as a plain streaming `AiService` invoked by the orchestrator after `system.chat(...)` returns — same UI behaviour, one extra line of wiring.

### 3.2a Skills for the Flink agent (`langchain4j-skills`)

LangChain4j ships a native **Agent Skills** module, `dev.langchain4j:langchain4j-skills:1.20.0-beta30` (same beta train as agentic/mcp; docs: https://docs.langchain4j.dev/tutorials/skills). It implements the `SKILL.md` convention (YAML front matter `name`/`description` + markdown body, optional `references/` files) with progressive disclosure:

* `FileSystemSkillLoader.loadSkills(Path)` / `ClassPathSkillLoader.loadSkills("skills")` / `Skill.builder()`.
* `Skills.from(skills).toolProvider()` registers an `activate_skill` tool (injects the skill body on demand) and, when a skill has resources, a `read_skill_resource` tool (any file in the skill dir except `SKILL.md` and `scripts/` becomes a resource).
* `skills.formatAvailableSkills()` produces the catalogue for the system prompt.
* **Skill-scoped tools**: `Skill.builder().tools(...)` / `.toolProviders(mcpToolProvider)` stay hidden until the skill is activated.
* It is a plain `ToolProvider`, so it attaches to an agentic agent exactly like MCP: `agentBuilder(SqlAuthor.class).toolProviders(flinkMcpTools, skills.toolProvider())`.

The Tessl tile `gamussa/flink-sql` already ships a conforming skill: `.tessl/plugins/gamussa/flink-sql/skills/flink-sql/SKILL.md` (333 lines) plus `references/{confluent-cloud,sql-patterns,kafka-patterns,troubleshooting,ptf-guide,udf-guide}.md`. Copy that directory into `src/main/resources/skills/flink-sql/` (or load it from `.tessl/` in dev) and wire:

```java
Skills flinkSkills = Skills.from(ClassPathSkillLoader.loadSkills("skills"));   // flink-sql (+ a small brewery-schema skill)

SqlAuthor sqlAuthor = AgenticServices.agentBuilder(SqlAuthor.class)
        .chatModel(workerModel)
        .toolProviders(flinkTools /* list-flink-tables, DESCRIBE */, flinkSkills.toolProvider())
        .systemMessageProvider(id -> FLINK_AUTHOR_PROMPT + "\n\nSkills:\n" + flinkSkills.formatAvailableSkills()
                + "\nActivate `flink-sql` before writing any statement; read `references/confluent-cloud.md` for Confluent-specific syntax.")
        .maxToolCallingRoundTrips(10)
        .build();
```

Effect on the demo: the 60-line Flink block in today's mega system prompt shrinks to a two-line policy; the agent pulls the full Window-TVF / `$rowtime` / watermark guidance only when it is about to write SQL, and the `SqlCritic` can read `references/troubleshooting.md` when a statement fails. The activation shows up in the TUI as a tool call ("→ activate_skill(flink-sql)"), which is itself a good talking point. Also worth a second, tiny `brewery-schema` skill holding the three Avro schemas that currently bloat the prompt.

Note: this is distinct from **Anthropic server-side Skills** (`AnthropicChatModel.builder().skills(AnthropicSkill.XLSX…)` + code-execution container, beta headers required), which cover document generation and are not what we need.

### 3.3 The UI seam: `AgentEvent`

Replace the five ad-hoc tui4j message records with one sealed hierarchy owned by the agent layer (no UI imports):

```java
sealed interface AgentEvent {
    record AgentStarted(String agent, String depth)            implements AgentEvent {}
    record AgentFinished(String agent, Object output, long ms) implements AgentEvent {}
    record ToolStarted(String agent, String tool, String args) implements AgentEvent {}
    record ToolFinished(String agent, String tool, String result) implements AgentEvent {}
    record Token(String text)                                  implements AgentEvent {}
    record Completed(String fullText, UsageSnapshot usage)     implements AgentEvent {}
    record Failed(String agent, Throwable error)               implements AgentEvent {}
}
```

`AgentListener` + `TokenStream` callbacks publish these to a `Consumer<AgentEvent>`; the TamboUI app subscribes with `runner().runOnRenderThread(...)`. This seam lets the two migrations proceed independently and be tested with a fake event source.

---

## 4. TamboUI migration

### 4.1 Concept mapping

| tui4j (today) | TamboUI (target) |
|---|---|
| `Model.init/update/view`, `UpdateResult` | `ToolkitApp.onStart()` / field state + `render()`; no update loop |
| `Program.send(msg)` from callback thread | `runner().runOnRenderThread(() -> …)` |
| `KeyPressMessage.key()` string switch | `element.onKeyEvent(e -> …)` with `e.isCtrlC()`, `e.isEscape()`, `e.isTab()`, `e.isChar('o') && e.hasCtrl()` → `EventResult` |
| `Textarea` (3 lines, Enter intercepted) | **Default: `textInput(state).onSubmit(Runnable)`** (single line; `state.text()` then `state.clear()` — tile rule `text-input-submit-pattern`). `TextAreaElement` exists but Enter always inserts `\n` and it has no submit hook, so multi-line would need an outer `onKeyEvent` intercept before the element handles the key — spike item |
| `Viewport` + manual `setContent(String)` | `list()` with `.displayOnly().stickyScroll().scrollbar().id("chat").focusable()`. `ListElement.elements(StyledElement…)` accepts arbitrary elements per row (a `panel` for a tool box, a `markdown` block for an agent turn), so each `ChatEntry` maps to one list element — no pre-wrapping strings needed unless multi-row items misbehave (tile skill `build-log-style-list` is the fallback) |
| Explicit `viewport.scrollUp/Down` for mouse wheel | Free with `TuiConfig.mouseCapture(true)` |
| `Spinner(SpinnerType.DOT)` + tick forwarding | `spinner(SpinnerStyle.DOTS, "Calling create-topics()…")` — animation handled by the runner's tick |
| `MarkdownRenderer` (214 LOC regex) | `markdown(text).overflow(Overflow.WRAP_WORD)` from `tamboui-toolkit-markdown`; streaming-safe |
| `Theme` (lipgloss `Style.newStyle().foreground(Color.color("12"))…`) | `brewmaster.tcss` stylesheet loaded via `ToolkitRunner.styleEngine(...)`; keep a tiny `Palette` enum for programmatic colors; follow `projector-safe-colors` rule (no `.dim()`/gray foregrounds) |
| `HeaderView` manual ANSI padding math | `row(text(appName).bold().onCyan(), text(model), spacer(), text(status))` — layout engine does the padding; use `CharWidth` never `length()` |
| `ToolResultView` bordered box | `panel(toolName + "()", richTextArea(result)).rounded()` collapsed/expanded toggle by swapping element |
| `CommandSuggestionView` popup | `stack(mainLayout, dialog(list(suggestions)))` shown while input starts with `/` |
| `WindowSizeMessage`, `setInitialSize` hack | Not needed — `ResizeEvent` + layout constraints |
| `Program.withAltScreen().withMouseCellMotion()` | `configure()` → `TuiConfig.builder().alternateScreen(true).mouseCapture(true).build()` |
| `--enable-native-access=ALL-UNNAMED` + jline-ffm | `tamboui-jline3-backend:0.4.0` depends on `org.jline:jline:3.25.1` (uber JLine). Our `jline-terminal-ffm:3.26.1` would mix JLine versions — align to one (force 3.25.1 or exclude/override consistently) and keep the `--enable-native-access` flag only if the FFM provider is still used |

### 4.2 Things to prototype early (risk burn-down)

1. **Chat pane rendering**: list-of-rows vs scrollable column of `markdown()` blocks. The list path is proven by the tile; the column path gives real markdown but needs `preferredSize` to wrap correctly (`pick-the-text-element` rule).
2. **Multi-line input + Enter-to-send** with `textArea`. If awkward, fall back to single-line `textInput` (what the tile's chat skill assumes).
3. **tmux behaviour** (mouse wheel, initial size) — this cost us real time on tui4j.
4. **GraalVM native** with the JLine3 backend — currently a documented "fallback if native unavailable", so not a blocker.

### 4.3 Tessl housekeeping

`.tessl/RULES.md` now loads both `jbaruch/tui4j-chat` style-defaults and the 13 TamboUI rules; they contradict (lipgloss color codes vs TCSS). When the TUI switch lands: `tessl uninstall jbaruch/tui4j-chat` and `tessl/maven-com-williamcallahan--tui4j`, bump the `tessl/maven-dev-langchain4j--*` tiles to 1.20, and refresh `gamussa/langchain4j-agentic` pins to 1.20.0 / beta30.

---

## 5. Migration plan and estimate

Assumes one senior Java engineer working with Claude Code, existing Confluent Cloud env + MCP server available, and the demo re-run after every phase. Days are focused engineering days.

| # | Phase | Work | Est. |
|---|---|---|---|
| 0 | **Dependency bump** | LC4J 1.11 → 1.20.0 (BOM) + `langchain4j-mcp:1.20.0-beta30`; add `-parameters`; **replace the removed SSE transport with `StreamableHttpMcpTransport` in `McpBridge`** (`type: streamable-http`, url `…/mcp`) and confirm the Confluent MCP server (local and hosted) serves Streamable HTTP — if it is SSE-only, upgrade the server or front it with an MCP proxy; re-run all 6 acts on the *current* single agent | **0.5–1 d** (+0.5 d if the MCP server needs a proxy) |
| 1 | **Event seam** | Introduce `AgentEvent` + `Consumer<AgentEvent>`; port `StreamBridge` to publish it; tui4j `AgentModel` consumes it. Pure refactor, no behaviour change | **0.5 d** |
| 2 | **Agentic core** | Add `langchain4j-agentic:1.20.0-beta30`; split prompts into `prompts/*.txt`; `BrewmasterAgent`, `ConfluentAgent`, `Presenter`, supervisor, `BrewerySessionState`; per-agent MCP tool filtering in `McpBridge`; `AgentListener` → events; `/model`, `/thinking`, `/clear`, `/mcp` rebuild the system; `UsageTracker` on all models | **2–3 d** |
| 3 | **Flink verify loop** | `FlinkStatement`, `CriticResult<T>`, `SqlAuthor`/`SqlCritic`/`SqlSubmitter`, `loopBuilder`, feedback plumbing; `langchain4j-skills` with the `flink-sql` tile skill (+ `brewery-schema` skill) attached to author and critic; error handler for `MissingArgumentException` | **1–1.5 d** |
| 4 | **Demo re-tune** | Re-rehearse Acts 1–6 against the multi-agent system; tune `supervisorContext`, descriptions, `responseStrategy`; `/report` command → `HtmlReportGenerator` opened in browser as the closing beat; update `demo/DEMO-SCRIPT.md` talking points (handoffs are now visible) | **1–1.5 d** |
| 5 | **TamboUI spike** | Throwaway `ToolkitApp` proving: chat pane, streaming markdown, textArea Enter handling, spinner, popup overlay, tmux mouse | **1 d** |
| 6 | **TamboUI port** | Rewrite `tui/` (≈1,700 LOC → est. 900–1,100): `BrewmasterApp extends ToolkitApp`, `brewmaster.tcss`, header/chat/tool/help/welcome elements, slash commands + suggestions, Ctrl+O expand toggle, terminal-too-small notice; delete `MarkdownRenderer`, `Theme`, tui4j deps; launcher/JVM flags | **2.5–3.5 d** |
| 7 | **Hardening** | GraalVM/jlink targets re-validated, README/docs, Tessl tile cleanup, full dress rehearsal on projector | **1 d** |
| | **Total** | | **9.5–13 days** (~2.5 calendar weeks solo with buffer; ~1.5 weeks if the TUI track (5–6) runs in parallel with 2–4 on a second person — the `AgentEvent` seam from phase 1 makes that split clean) |

**Order matters:** 0 → 1 first (cheap, de-risks everything). Then the agentic track (2–4) before the TUI track (5–7): it carries the demo's new story, and the tui4j UI keeps working through the event seam. If the conference date forces a cut, ship phases 0–4 on tui4j and defer TamboUI.

---

## 6. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Supervisor picks the wrong specialist or loops (non-deterministic) | Demo stalls | Strong `@Agent` descriptions, `supervisorContext` policies, `maxToolCallingRoundTrips(10)` on every tool-using agent and `maxAgentsInvocations` on the supervisor, rehearse each act ≥3×, keep a `/direct <agent>` escape hatch that invokes a specialist without the supervisor |
| Loss of token-by-token SQL streaming (Act 3) | Weaker "watch it write SQL" moment | Presenter streams the explanation; show SQL from `beforeToolExecution` args as a highlighted block the instant it is submitted; the visible author→critic loop is a better story |
| Beta API drift (agentic beta30) | Compile breaks on next bump | Pin all betas together; tile `check_versions.sh` in CI; avoid `agentic-patterns`/A2A unless needed |
| Ollama models as supervisor planner | Bad plans, tool-call failures | Planner always Anthropic; Ollama only for workers via `/model` |
| TamboUI 0.4.0 is young; multi-line input + overlay popup may need custom elements | +1–2 days | Spike phase 5 is a gate; `wrap-widget-as-element` skill covers custom elements; single-line input fallback |
| SSE `HttpMcpTransport` **removed** in 1.19; Confluent MCP server may be SSE-only | Blocks phase 0 entirely | Probe the server's `/mcp` endpoint first thing; upgrade the server (the Confluent MCP server is open source and current MCP SDKs ship Streamable HTTP) or run an MCP proxy in front; keep `type: stdio | streamable-http` in `McpServerConfig` and drop `sse` |
| Cross-turn state (schemas registered, topics) not visible to agents | Agent re-registers schema / wrong assumptions | `@MemoryId`-keyed persistent `AgenticScope` + `BrewerySessionState` summary in `supervisorContext`; `/clear` evicts the scope |
| Prompt cache efficiency drops (many smaller prompts) | Slightly slower turns | `cacheSystemMessages(true)` + `cacheTools(true)` per model; tool lists are smaller per agent so caches are cheaper |

---

**Unverified today:** whether the Confluent MCP server (local `localhost:8080`, hosted `mcp-confluent.ai-assisted.engineering`) already serves Streamable HTTP on `/mcp`. A probe from this sandbox could not reach either host. Run from a normal shell before phase 0:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"probe","version":"0"}}}' \
  http://localhost:8080/mcp
```

A 200 with a JSON or SSE body means Streamable HTTP is available; 404 means the server is SSE-only and needs an upgrade or proxy.

## 7. Decisions (taken 2026-09-07)

| # | Decision | Outcome |
|---|---|---|
| 1 | Agent split | **Brewmaster (business) + Confluent + Flink loop (tech stack)** — three specialists under the supervisor, as in §3.1 |
| 2 | Streaming trade-off | **Accepted** — Presenter streams the narrative; Flink SQL is shown at submit time from the tool call |
| 3 | Input widget | **Single-line** `textInput(state).onSubmit(...)` — drops the multi-line spike item from phase 5 |
| 4 | Ship order | **Agentic first** (phases 0–4), TamboUI second (phases 5–7) |

Status line: `docs/agentic-migration-plan.md` is the source of truth; **Status:** approved, phase 0 in progress.

## 8. Progress log

* **2026-09-07 — phase 0 code done on branch `feat/agentic-migration`** (uncommitted): BOM 1.20.0 + `langchain4j-mcp:1.20.0-beta30`, `-parameters` on every `JavaCompile` (verified via `MethodParameters` in bytecode), `McpBridge` now builds `StreamableHttpMcpTransport` (60 s timeout) and warns on legacy `sse`/`/sse` config, `McpServerConfig` gained `streamable-http` as default type with `isHttp()`/`isLegacySse()`, sample configs and demo checklist point at `/mcp`. `compileJava` + `shadowJar` succeed; runtime classpath resolves a single `langchain4j-core:1.20.0`. **Not done at that point:** re-running the six acts.
* **2026-09-07 — transport verified against the local Confluent MCP server** (`~/projects/ai/mcp-confluent`, v1.6.0). Findings: the server ships a native Streamable HTTP transport (`server.transports: [http, sse]` in `config.yaml`, endpoint `/mcp`; `--transport` flag and the YAML key are mutually exclusive); its native Kafka module is built for **Node 22** (`.nvmrc` = v22.22.1) and fails to load on the default Node 26, so start it with `~/.nvm/versions/node/v22.22.2/bin/node dist/index.js -c config.yaml -e .env`. The Java smoke test (`McpBridge` → `StreamableHttpMcpTransport`) connected in 412 ms, discovered **58 tools** (up from the 24 in the original plan — notably `describe-flink-table`, `get-flink-statement-exceptions`, `check-flink-statement-health`, `detect-flink-statement-issues`, `get-flink-statement-profile`, which are exactly what `SqlCritic` needs), and a real `list-topics` call returned the cluster's topics. Phase 0 transport risk is closed.
* **2026-09-07 — full-stack run on the 1.20 build**: `./kafka-agent` launched in tmux against the local server; header shows `MCP: 58 tools`; a real turn ("list my brewery topics") streamed through Anthropic, called `search-topics-by-name`, and the usage line updated (820 in / 177 out, $0.0051, 3.6 s). One stale value fixed on the way: `docs/brewmaster-config.yaml` (which `~/.config/kafka-agent/config.yaml` symlinks to) still named the retired model `claude-sonnet-4-20250514` → now `claude-sonnet-4-6`, matching `ModelSwitcher`. **Phase 0 is complete.** The six-act rehearsal is deferred to phase 4, where it belongs. Committed as `103dc37` + `a8586a6`.
* **2026-09-07 — phase 1 done (`AgentEvent` seam).** `agent/AgentEvent` is the sealed hierarchy from §3.3 (`AgentStarted`, `AgentFinished`, `ToolStarted`, `ToolFinished`, `Token`, `Completed`, `Failed`) plus an `AgentEvent.Sink`. `StreamBridge` now takes a sink instead of the tui4j `Program` and has no UI imports; `AgentApp` adapts with one lambda (`event -> program.send(new AgentEventMessage(event))`). `AgentModel` handles everything in one exhaustive `switch` over the sealed type; the five ad-hoc message records are deleted. Sub-agent lifecycle is already rendered (agent name next to the spinner, "↳ agent done in 1.2 s" trace lines) so phase 2 only has to publish events. Smoke-tested live: streamed answer + `list-topics` call render as before, and narration before/after a tool call now gets a paragraph break. Committed as `b5945d8`.
* **2026-09-07 — phase 2 core running (`agentic/` package).** `Agents` (BrewmasterAgent, ConfluentAgent, FlinkAgent, BrewOpsSupervisor, Presenter, BrewerySystem root with `@MemoryId`), `AgentSystemFactory` (supervisor SUMMARY + CHAT_MEMORY_AND_SUMMARIZATION, Anthropic planner, per-specialist MCP scopes via `McpBridge.getToolProvider(Predicate)`, presenter last with a streaming model), `UiAgentListener` (inherited by sub-agents → `AgentEvent`s), `AgenticBridge` (root call on a virtual thread, presenter `TokenStream` forwarded), `ConversationFactory` + `ConversationBridge` so the TUI switches between `single` and `agentic` at runtime (`/agentic on|off`, header shows the mode), `/report` writes the `HtmlReportGenerator` report and opens it, prompts split into `resources/prompts/*.txt` with `agentic.prompts.<name>` overrides, `agentic.enabled` config key. **Verified live:** the composed root method returning `TokenStream` works on beta30 (spike question closed); a real turn ran supervisor → confluent (15 tool calls, 39 s) → presenter streaming, hand-offs and tool boxes rendered. Two lessons: (1) the supervisor planner reads the scope key **`request`** — a differently named root argument arrives empty; (2) offering `list-clusters`/`list-environments` made the specialist enumerate infrastructure 6× in one turn — removed from the Confluent scope and the prompt now says the server is pre-configured. Cost of that first turn: $0.12 / 26k input tokens / 54 s, versus one call in single mode — after the scope/prompt fix the same turn is 2 tool calls, $0.057, 32 s. Committed as `fb6d5e5`.
* **2026-09-07 — phase 3 done (Flink verify loop + skills).** `FlinkTypes` (`FlinkStatement`, `CriticResult` records as structured output), `FlinkAgents` (`sqlAuthor` → `sqlCritic` loop, max 3, exits on `criticResult.successful()`; then `sqlSubmitter`), composed with `loopBuilder`/`sequenceBuilder` as the `flink` specialist. `langchain4j-skills:1.20.0-beta30` bundles the `flink-sql` tile skill under `resources/skills`; author and critic get `activate_skill`/`read_skill_resource`. New `flinkOps` specialist owns existing statements (list/health/exceptions/delete). **Act 3 ran live end to end:** brewmaster (18 s) → sqlAuthor (activated the skill, read `confluent-cloud.md`, DESCRIBE ×2, drafted `brewery-temp-alerts`, 41 s) → sqlCritic **PASS** (32 s) → sqlSubmitter (create + 4 health polls, RUNNING, 27 s) → presenter streamed. 154 s, $0.43, 104k input tokens (the skill content is read by both author and critic — a phase 4 tuning target). Then a cleanup turn routed to `flinkOps`, which deleted the statement and confirmed. Two defects found and fixed on the way: the presenter **reconstructed SQL from the supervisor's prose summary** (legacy `TUMBLE` syntax, invented sensor ids) — it now receives the submitted statement verbatim through `.context(scope → …)` and the root sequence resets per-turn artifacts; and the author wasted a 459-line `list-flink-tables` call — it now DESCRIBEs the named tables directly. Tool boxes keep call arguments, so Ctrl+O on `create-flink-statement` shows the real SQL. Committed as `217dc6b`.
* **2026-09-07 — official Confluent skills + a routing incident.** Vendored `confluent-cloud-flink-sql` from https://github.com/confluentinc/agent-skills (Apache-2.0, pinned `914d95e`, script `scripts/vendor-skills.sh`, provenance in `resources/skills/SOURCES.md`) alongside the tile's `flink-sql`; author and critic activate the official one first. Verified live: the critic **failed the first draft** (INSERT column order vs sink) and the loop recovered → PASS → RUNNING. Two findings:
  1. **Bare table names fail DESCRIBE** — the MCP server resolves its default catalog/database to IDs (`env-j03r6m.lkc-6vyxz8`) while Confluent Cloud Flink names them after the environment and cluster display names (`current-2025-demo` / `maestro_gcp`). Both agents burned a 461-line `list-flink-tables` plus catalog/database listings to discover this. Fix: `confluent.flink.catalog-name` / `database-name` config keys, injected into the author/critic prompts as a mandatory `catalogName`/`databaseName` hint. The MCP server's `.env` should also get `FLINK_CATALOG_NAME` / `FLINK_DATABASE_NAME` set to those display names (not verified — secrets file).
  2. **Mis-route with side effects.** A "delete the statement" request was routed to the *authoring* workflow, which drafted and submitted a fresh statement, and the planner re-invoked it twice more within the turn (~$1.50 before the JVM was killed; the environment was then cleaned with a direct MCP call). Fixes: explicit HARD RULE in `supervisor-context` (delete/stop/list/status → `flinkOps`), `flinkOps` listed before `flink` with DELETE/STOP/LIST/STATUS in its description and "NEVER for deleting" in `flink`'s, a **short-circuit guard** in the authoring workflow (author returns `NOT-APPLICABLE`, critic passes it, submitter submits nothing), and `maxAgentsInvocations` 8 → 4. Also learned: the TUI drops Enter while a turn is running and has no cancel — a `/stop` or Esc-to-cancel is a phase 4 item worth having on stage. Committed as `cdea7ba`.
* **2026-09-07 — phase 5/6 started: three-panel TamboUI UI (`tui2/BreweryApp`).** Requested after rehearsal: the single-column tui4j view drowns the narrative in tool boxes. New layout: header · **Chat** (you + presenter, 64 %) │ **Activity** (hand-offs and tool calls, one row each: `▸ confluent working…` → `✔ confluent 21 s → PASS`, `⚙ list-topics (3 lines)`) · **Prompt** · usage line. Enter on an Activity row opens a detail overlay (`dialog` + scrollable `richTextArea`) with the tool arguments and result; Esc closes. Tab cycles Prompt → Chat → Activity; Ctrl+C quits (bare `q` unbound). Selected with `ui.kind: tamboui` or `KAFKA_AGENT_UI=tamboui`; tui4j stays the default until the port is complete. Both UIs share `ConversationFactory` and the `AgentEvent` seam — the TamboUI adapter is one `runOnRenderThread` lambda. TamboUI 0.4.0 coexists with tui4j on one classpath by excluding the backend's uber `org.jline:jline` and letting the 3.26.1 modules serve both. Verified live in tmux: layout, spinner + label in the header, activity rows updating in place, presenter text streaming into Chat.

## 8. Sources

* Maven Central metadata for `dev.langchain4j:*` (1.20.0 / 1.20.0-beta30, 2026-09-04) and `dev.tamboui:*` (0.4.0)
* https://docs.langchain4j.dev/tutorials/agents — agentic module reference
* https://github.com/langchain4j/langchain4j/releases — 1.18–1.20 notes
* `.tessl/plugins/gamussa/langchain4j-agentic` (docs, gotchas, best-practice rules)
* `.tessl/plugins/jbaruch/tamboui` (13 rules, 5 skills) — https://tessl.io/registry/jbaruch/tamboui
* https://github.com/tamboui/tamboui — `AGENTS.md`, `ToolkitApp`, `ToolkitRunner`, `Toolkit`, `StyledElement`, `TextAreaElement`, `ListElement`, `KeyEvent`, `EventRouter`, `docs/markdown.adoc`, `docs/api-levels.adoc`, `docs/bindings.adoc`
* https://github.com/jbaruch/jclaw-demo — the tile author's reference LangChain4j + TamboUI chat TUI (Kotlin, `JclawTui.kt`); closest existing example of our target UI
* GitHub contents API for `langchain4j-mcp/.../transport/http` at tags 1.18.0 and 1.20.0 (transport removal); LangChain4j apidocs for `AgentBuilder`, `SupervisorAgentService`, `AgentListener`; issue #4795 / PR #4843
