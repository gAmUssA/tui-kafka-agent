# AI Agent TUI App: LangChain4j + tui4j

## Research Findings

### tui4j — What We're Working With

tui4j (v0.2.5, Maven Central: `com.williamcallahan:tui4j`) is a Java port of Go's **Bubble Tea** framework using the **Elm Architecture** (Model → Update → View). It ports three key libraries from the charmbracelet ecosystem:

| Go Library | Java Package         | What It Does                                                                                             |
|------------|----------------------|----------------------------------------------------------------------------------------------------------|
| bubbletea  | `compat.bubbletea.*` | Core TUI framework (Program, Model, Message, Command)                                                    |
| bubbles    | `compat.bubbles.*`   | UI components: **viewport**, **textarea**, **table**, **progress**, **spinner**, **list**, **textinput** |
| lipgloss   | lipgloss port        | Styling: colors, borders, layout, rendering                                                              |

**Architecture pattern**: You implement a `Model` interface with three methods:
- `init()` → returns initial Command
- `update(Message msg)` → handles events, returns `UpdateResult<Model>` + optional Command
- `view()` → returns a `String` that IS your entire UI

Messages flow in (keypresses, tick events, custom messages), the model updates, and the view re-renders. tui4j handles terminal raw mode, alternate screen buffer, and efficient redrawing.

**Key reference**: [Brief](https://github.com/WilliamAGH/brief) is an existing terminal AI chat client built with tui4j that supports slash commands, tool execution, and OpenAI-compatible providers. This proves the pattern works for AI chat apps.

### LangChain4j — AI Agent Capabilities

LangChain4j provides everything needed for an intelligent agent:

- **AI Services**: Declarative interfaces with `@Tool` annotations for function calling
- **Streaming**: `TokenStream` and `StreamingChatModel` for real-time token-by-token responses
- **Chat Memory**: `MessageWindowChatMemory` for conversation history
- **Tool/Function Calling**: `@Tool` annotated methods auto-converted to `ToolSpecification`
- **MCP Client**: Connect to external MCP servers for tool discovery and execution
- **Multi-Agent**: Supervisor patterns, router agents, expert delegation
- **Provider Support**: Anthropic (primary), plus OpenAI, Gemini, Mistral, Ollama if needed
- **Anthropic-Specific**: Prompt caching, extended thinking, tool caching, streaming with partial tool calls

### Feasibility Assessment

| Feature                                    | Feasible?  | Notes                                                                      |
|--------------------------------------------|------------|----------------------------------------------------------------------------|
| Chat with streaming responses              | ✅ Yes      | LangChain4j `TokenStream` → tui4j viewport append                          |
| Tool/function calling with visual feedback | ✅ Yes      | `@Tool` methods + spinner/progress during execution                        |
| Slash commands (like Brief)                | ✅ Yes      | Parse input starting with `/`, map to actions                              |
| Multi-panel layout (chat + sidebar)        | ✅ Yes      | lipgloss layout + compose multiple model views                             |
| MCP server integration                     | ✅ Yes      | LangChain4j `McpToolProvider` built-in                                     |
| Conversation memory                        | ✅ Yes      | `MessageWindowChatMemory` persists across turns                            |
| Model/provider switching                   | ✅ Yes      | Swap between Claude Sonnet/Haiku/Opus at runtime                           |
| Markdown rendering in terminal             | ⚠️ Partial | Basic formatting via ANSI; no full glamour port yet                        |
| Multi-agent orchestration                  | ✅ Yes      | LangChain4j supervisor/router agent patterns                               |
| Kafka/Flink integration as tools           | ✅ Yes      | Custom `@Tool` methods calling Confluent APIs                              |
| Extended thinking (Claude)                 | ✅ Yes      | `thinkingType("enabled")` — show reasoning in collapsible TUI section      |
| Prompt caching (Claude)                    | ✅ Yes      | `cacheSystemMessages` + `cacheTools` — faster repeat calls in TUI sessions |

---

## Implementation Plan

### Project: `kafka-agent-tui` — A Streaming-Data-Aware AI Agent in the Terminal

An AI agent that lives in your terminal, understands Kafka and Flink, can execute tools against Confluent Cloud, and streams responses in real-time with a beautiful TUI.

### Phase 1: Project Scaffolding & Basic Chat (Days 1–2)

**Goal**: Get a working chat loop with streaming AI responses in a tui4j terminal UI.

**Setup**:
```kotlin
// build.gradle.kts
plugins {
    java
    application
}

dependencies {
    // TUI framework
    implementation("com.williamcallahan:tui4j:0.2.5")
    
    // LangChain4j core + Anthropic
    implementation("dev.langchain4j:langchain4j:1.9.1")
    implementation("dev.langchain4j:langchain4j-anthropic:1.9.1")
    
    // Config
    implementation("org.yaml:snakeyaml:2.2")
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.example.agent.AgentApp")
}
```

**Architecture — Three-Layer Design**:

```
┌─────────────────────────────────────────┐
│              TUI Layer (tui4j)           │
│  Model ←→ Update ←→ View                │
│  TextInput | Viewport | Spinner | List   │
└─────────────┬───────────────────────────┘
              │ Custom Messages
┌─────────────▼───────────────────────────┐
│           Agent Layer (LangChain4j)      │
│  AI Service | Tools | Memory | Streaming │
└─────────────┬───────────────────────────┘
              │ Tool Calls
┌─────────────▼───────────────────────────┐
│           Tools Layer                    │
│  Kafka Admin | Flink SQL | Web Search    │
│  File Ops | Shell Exec | MCP Servers     │
└─────────────────────────────────────────┘
```

**Core Model structure**:

```java
public class AgentModel implements Model {
    // UI state
    private TextAreaModel inputArea;      // user input
    private ViewportModel chatViewport;   // scrollable chat history
    private SpinnerModel spinner;         // thinking indicator
    
    // Agent state  
    private boolean isThinking;
    private StringBuilder currentResponse;
    private List<ChatEntry> chatHistory;
    
    // LangChain4j (injected, not part of model state)
    private final AgentService agentService;
}
```

**Custom Messages for bridging async AI responses into tui4j's event loop**:

```java
record StreamTokenMessage(String token) implements Message {}
record StreamCompleteMessage(String fullResponse) implements Message {}
record ToolExecutingMessage(String toolName) implements Message {}
record ToolCompleteMessage(String toolName, String result) implements Message {}
record ErrorMessage(String error) implements Message {}
```

**Deliverable**: Type a message → see streaming AI response token-by-token in a scrollable viewport.

### Phase 2: Tool Integration & Visual Feedback (Days 3–4)

**Goal**: Add tools the agent can call, with live TUI feedback.

**Define tools as LangChain4j `@Tool` classes**:

```java
public class KafkaTools {
    @Tool("List all Kafka topics in the Confluent Cloud cluster")
    public String listTopics() { /* Confluent Admin API call */ }
    
    @Tool("Describe a Kafka topic including partitions, configs, and consumer groups")
    public String describeTopic(@P("topic name") String topicName) { /* ... */ }
    
    @Tool("Produce a test message to a Kafka topic")
    public String produceMessage(
        @P("topic name") String topic, 
        @P("message key") String key, 
        @P("message value as JSON") String value) { /* ... */ }
    
    @Tool("Get consumer group lag for a topic")
    public String getConsumerLag(
        @P("consumer group ID") String groupId) { /* ... */ }
}

public class FlinkTools {
    @Tool("Submit a Flink SQL statement to Confluent Cloud")
    public String submitFlinkSql(@P("Flink SQL statement") String sql) { /* ... */ }
    
    @Tool("List running Flink SQL statements")
    public String listStatements() { /* ... */ }
}
```

**Wire into AI Service with Anthropic Claude**:

```java
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;

// Streaming model for real-time token delivery to the TUI
StreamingChatModel streamingModel = AnthropicStreamingChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName("claude-sonnet-4-20250514")
    .maxTokens(4096)
    .temperature(0.7)
    .cacheSystemMessages(true)   // Anthropic prompt caching — saves cost on system prompt
    .cacheTools(true)            // Cache tool definitions across calls
    .logRequests(false)
    .logResponses(false)
    .build();

// Non-streaming model for quick one-shot calls (e.g., slash command routing)
ChatModel chatModel = AnthropicChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName("claude-sonnet-4-20250514")
    .maxTokens(4096)
    .cacheSystemMessages(true)
    .cacheTools(true)
    .build();

interface AgentAssistant {
    TokenStream chat(@MemoryId String sessionId, @UserMessage String message);
}

AgentAssistant assistant = AiServices.builder(AgentAssistant.class)
    .streamingChatModel(streamingModel)
    .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(50))
    .tools(new KafkaTools(), new FlinkTools(), new ShellTools())
    .build();
```

**Anthropic-specific features to leverage**:
- **Prompt caching** (`cacheSystemMessages` + `cacheTools`): System prompt and tool definitions are cached server-side, reducing latency and cost on subsequent calls — huge win for a TUI where the same tools are used repeatedly
- **Extended thinking** (optional, for complex reasoning):
  ```java
  .thinkingType("enabled")
  .thinkingBudgetTokens(2048)
  .returnThinking(true)    // surface reasoning in a collapsible TUI section
  .sendThinking(true)      // maintain thinking chain across turns
  ```
- **Tool use**: Claude's tool calling is best-in-class — perfect for the agent pattern

**TUI feedback during tool execution**:
- When a tool call is detected → show spinner with tool name: `⠋ Calling listTopics()...`
- When tool completes → show result in a styled box before the AI continues
- Tool results rendered as a collapsible section in the chat viewport

**Deliverable**: Ask "What topics do I have?" → agent calls `listTopics()` → spinner shows → results displayed → agent summarizes.

### Phase 3: Slash Commands & UI Polish (Days 5–6)

**Goal**: Add power-user features and make the UI shine.

**Slash commands** (parsed from input starting with `/`):

| Command         | Action                                          |
|-----------------|-------------------------------------------------|
| `/model <name>` | Switch Claude model (`sonnet`, `haiku`, `opus`) |
| `/thinking`     | Toggle extended thinking mode on/off            |
| `/clear`        | Clear chat history and memory                   |
| `/tools`        | List available tools                            |
| `/history`      | Show conversation summary                       |
| `/export`       | Export chat to markdown file                    |
| `/mcp <url>`    | Connect to an MCP server and load its tools     |
| `/topics`       | Quick shortcut: list Kafka topics               |
| `/sql <query>`  | Quick shortcut: submit Flink SQL                |
| `/help`         | Show available commands                         |

**UI Layout** (composed view):

```
┌──────────────────────────────────────────────┐
│ 🤖 kafka-agent │ claude-sonnet │ 3 tools      │  ← header bar
├──────────────────────────────────────────────┤
│                                              │
│  You: What topics do I have?                 │
│                                              │
│  Agent: ⠋ Calling listTopics()...            │  ← viewport
│                                              │
│  ┌─ Tool Result: listTopics ───────────────┐ │
│  │ • orders (6 partitions)                 │ │
│  │ • users (3 partitions)                  │ │
│  │ • events (12 partitions)                │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  Agent: You have 3 topics in your cluster... │
│                                              │
├──────────────────────────────────────────────┤
│ > Type a message... (/ for commands)         │  ← input area
└──────────────────────────────────────────────┘
```

**Styling with lipgloss**:
- Color-coded roles (user = cyan, agent = green, tool = yellow, error = red)
- Bordered boxes for tool results
- Subtle dimmed text for timestamps and metadata

**Deliverable**: Polished, keyboard-navigable TUI with slash commands, styled output, and header bar showing connection state.

### Phase 4: Advanced Agent Features (Days 7–8)

**Goal**: Add MCP support, multi-agent routing, and Kafka consumer streaming.

**MCP Integration**:
```java
// Connect to any MCP server at runtime via /mcp command
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl(mcpUrl)
    .build();
McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .build();
mcpClient.initialize();

// Dynamically add MCP tools to the agent
McpToolProvider mcpToolProvider = McpToolProvider.builder()
    .mcpClients(mcpClient)
    .build();
```

**Live Kafka Consumer in TUI** (stretch goal):
- `/consume <topic>` starts a background Kafka consumer
- Messages stream into a dedicated viewport panel
- Agent can observe and comment on the data flowing through

**Multi-Agent Router** (stretch goal):
```java
interface RouterAgent {
    @UserMessage("""
        Route this request to the appropriate expert:
        - kafka: for topic, producer, consumer questions
        - flink: for SQL, stream processing, table queries
        - general: for everything else
        Request: {{it}}
        """)
    String route(String request);
}
```

**Deliverable**: MCP server connection, dynamic tool loading, optionally live Kafka consumption view.

### Phase 5: Packaging & Demo Prep (Day 9–10)

**Goal**: Make it installable and demo-ready.

- **GraalVM native-image** or **jlink** for fast startup
- **Config file** at `~/.config/kafka-agent/config.yaml` for Anthropic API key, cluster endpoints
- **Default config**:
  ```yaml
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-20250514
    max-tokens: 4096
    cache-system-messages: true
    cache-tools: true
    thinking:
      enabled: false
      budget-tokens: 2048
  confluent:
    bootstrap-servers: ${CONFLUENT_BOOTSTRAP}
    api-key: ${CONFLUENT_API_KEY}
    api-secret: ${CONFLUENT_API_SECRET}
    flink:
      environment-id: ${FLINK_ENV_ID}
      org-id: ${CONFLUENT_ORG_ID}
  ```
- **Demo script** showing:
  1. Launch the agent
  2. Ask about Kafka topics (tool call demo)
  3. Submit a Flink SQL query
  4. Toggle `/thinking` for a complex architecture question
  5. Connect to an MCP server
  6. Stream consume from a topic

---

## Key Technical Decisions

| Decision            | Choice                                       | Rationale                                                                                                      |
|---------------------|----------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Streaming bridge    | Custom `Message` types + `Program.send()`    | tui4j's event loop requires messages; streaming tokens arrive on another thread                                |
| LLM Provider        | **Anthropic Claude** (direct API)            | Best tool calling, prompt caching reduces cost in repeated TUI sessions, extended thinking for complex queries |
| LangChain4j version | **1.9.1** (latest stable)                    | Full Anthropic support incl. thinking, caching, streaming                                                      |
| Anthropic model     | `claude-sonnet-4-20250514`                   | Fast + capable; swap to `claude-3-5-haiku` for speed or `claude-opus-4-20250514` for complex reasoning         |
| Build tool          | Gradle with Kotlin DSL                       | Matches tui4j's own build system                                                                               |
| Java version        | 21+                                          | Virtual threads for async tool execution; tui4j requires modern Java                                           |
| Config              | YAML via SnakeYAML                           | Simple, human-readable; store API keys and cluster config                                                      |
| Anthropic caching   | `cacheSystemMessages` + `cacheTools` enabled | System prompt + tool defs cached server-side = faster + cheaper repeated calls                                 |

## Risks & Mitigations

| Risk                                    | Mitigation                                                                                 |
|-----------------------------------------|--------------------------------------------------------------------------------------------|
| tui4j is young (v0.2.5, 9 stars)        | Brief proves it works for chat; pin version; contribute fixes upstream                     |
| Thread safety between streaming and TUI | Use `Program.send()` to post messages from streaming callback thread into tui4j event loop |
| Textarea/viewport may lack features     | Fall back to raw string rendering; viewport scrolling is confirmed working                 |
| Token streaming may feel laggy          | Buffer tokens into word-sized chunks before sending to UI                                  |

## File Structure

```
kafka-agent-tui/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/example/agent/
│   ├── AgentApp.java              # main entry point
│   ├── tui/
│   │   ├── AgentModel.java        # main tui4j Model
│   │   ├── ChatEntry.java         # chat message record
│   │   ├── messages/              # custom Message types
│   │   │   ├── StreamTokenMsg.java
│   │   │   ├── ToolExecMsg.java
│   │   │   └── SlashCommandMsg.java
│   │   ├── views/                 # view rendering helpers
│   │   │   ├── ChatView.java
│   │   │   ├── HeaderView.java
│   │   │   └── ToolResultView.java
│   │   └── styles/
│   │       └── Theme.java         # lipgloss styles
│   ├── agent/
│   │   ├── AgentService.java      # LangChain4j AI Service interface
│   │   ├── AnthropicConfig.java   # Anthropic model/provider config
│   │   ├── ModelSwitcher.java     # swap between sonnet/haiku/opus at runtime
│   │   └── StreamBridge.java      # bridges TokenStream → tui4j Messages
│   ├── tools/
│   │   ├── KafkaTools.java        # @Tool methods for Kafka
│   │   ├── FlinkTools.java        # @Tool methods for Flink SQL
│   │   ├── ShellTools.java        # @Tool for shell commands
│   │   └── McpBridge.java         # dynamic MCP tool loading
│   └── config/
│       └── AppConfig.java         # YAML config loader
└── src/main/resources/
    └── default-config.yaml        # Anthropic + Confluent defaults
```

## Next Steps

1. **Validate tui4j basics**: Build a minimal "hello world" TUI with textarea input + viewport output
2. **Validate streaming bridge**: Get LangChain4j `TokenStream` tokens flowing into tui4j's event loop
3. **Iterate from there**: Each phase builds on the previous, with a working app at each stage