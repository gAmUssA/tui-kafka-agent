# kafka-agent / Brewmaster Agent

A terminal AI agent for Apache Kafka and Flink on Confluent Cloud. It talks to the
[Confluent MCP server](https://github.com/confluentinc/mcp-confluent) for tools, runs on
[LangChain4j](https://docs.langchain4j.dev) 1.20 with the `langchain4j-agentic` multi-agent
module, and renders in the terminal with tui4j. The bundled demo profile is the
**Brewmaster Agent**: a brewery IoT pipeline built, monitored and sabotaged through conversation.

## Run

Requirements: Java 21, an `ANTHROPIC_API_KEY`, and a running Confluent MCP server that serves
Streamable HTTP (the SSE transport is no longer supported by the client).

```bash
# 1. Confluent MCP server (its native Kafka module needs Node 22)
cd ~/projects/ai/mcp-confluent
~/.nvm/versions/node/v22.22.2/bin/node dist/index.js -c config.yaml -e .env   # config: server.transports: [http, sse]

# 2. Config: ~/.config/kafka-agent/config.yaml  (the demo profile is docs/brewmaster-config.yaml)

# 3. Build and run — needs a real TTY
./gradlew shadowJar
./kafka-agent
```

The header shows `app │ provider:model │ mode │ MCP: N tools`. `mode` is `agentic` or `single`.

Two front ends share the same agent layer:

| UI | Select with | Layout |
|---|---|---|
| `tui4j` (default) | `ui.kind: tui4j` | single column: chat with inline tool boxes (Ctrl+O expands) |
| `tamboui` | `ui.kind: tamboui` or `KAFKA_AGENT_UI=tamboui ./kafka-agent` | three panels: **Chat** (you + presenter) │ **Activity** (hand-offs, tool calls, one row each; Enter opens the detail overlay, Esc closes) · **Prompt**; Tab cycles panes |

## Two modes

| Mode | What runs on a turn | When |
|---|---|---|
| `single` | one streaming AiService with every MCP tool | the original assistant; `/agentic off` |
| `agentic` | `sequence( supervisor( brewmaster, confluent, flink, flinkOps ), presenter )` | `agentic.enabled: true` or `/agentic on` |

In agentic mode the supervisor (`brewops`, always an Anthropic model) reads the request and dispatches
specialists, each with its own prompt and its own scoped tool list:

| Specialist | Owns | Tools |
|---|---|---|
| `brewmaster` | brewing science: sensor readings, thresholds, diagnosis | none |
| `confluent` | topics, tags, Schema Registry, produce/consume | Kafka + SR tools |
| `flink` | new statements as a workflow: `sqlAuthor` → `sqlCritic` loop (typed verdict) → `sqlSubmitter` | DESCRIBE + submit tools, `flink-sql` skill |
| `flinkOps` | existing statements: status, health, exceptions, delete | Flink ops tools |
| `presenter` | streams the answer; the only streaming agent, therefore last | none |

The scope is persistent per session (`@MemoryId`), so state survives between turns; `/clear` evicts it.
Hand-offs and tool calls appear live in the chat as trace lines and collapsible tool boxes.

## Slash commands

| Command | Effect |
|---|---|
| `/help` | list commands |
| `/agentic [on\|off]` | show or toggle multi-agent mode |
| `/report` | write the `AgentMonitor` HTML report (topology, timeline, tokens) and open it |
| `/model <name>` / `/model ollama:<name>` | switch model or provider (`sonnet`, `haiku`, `opus` aliases) |
| `/thinking` | toggle extended thinking |
| `/mcp [name] <url>` / `/mcp disconnect [name]` | manage MCP servers (Streamable HTTP or stdio via config) |
| `/tools` | list tools by server |
| `/usage [reset]` | session tokens, cost, latency |
| `/topics`, `/sql <query>` | shortcuts routed through the agent |
| `/setup`, `/reset` | demo prompts from `demo.setup` / `demo.reset` in the config |
| `/clear`, `/quit` | clear history (and the agentic scope) / exit |

Keys: `Enter` send, `Tab` accept a command suggestion, `Ctrl+O` expand/collapse tool output,
`PgUp`/`PgDn` or mouse wheel scroll, `Esc`/`Ctrl+C` quit.

## Configuration

`src/main/resources/default-config.yaml` documents every key; `~/.config/kafka-agent/config.yaml`
overlays it and supports `${ENV_VAR}` substitution. The demo profile lives in
`docs/brewmaster-config.yaml`. Highlights:

```yaml
provider: anthropic            # or ollama (workers only; the planner stays on Anthropic)
anthropic: { api-key: ${ANTHROPIC_API_KEY}, model: claude-sonnet-4-6 }
agentic:
  enabled: true
  planner-model: claude-sonnet-4-6
  prompts: { brewmaster: "..." }   # optional overrides of resources/prompts/<name>.txt
mcp:
  servers:
    confluent: { type: streamable-http, url: http://localhost:8080/mcp }
```

Prompts: `src/main/resources/prompts/{brewmaster,confluent,sql-author,sql-critic,sql-submitter,flink-ops,presenter,supervisor-context}.txt`.
Skills (`src/main/resources/skills/`, loaded with `langchain4j-skills`, available to the Flink author and critic):
`confluent-cloud-flink-sql` from Confluent's official [agent-skills](https://github.com/confluentinc/agent-skills)
repo (dialect traps, reserved words, CC troubleshooting) and `flink-sql` from the `gamussa/flink-sql` Tessl tile.
Refresh with `./scripts/vendor-skills.sh [sha]`; see `skills/SOURCES.md` for pins and licenses.

## Code map

```
com.example.agent
├── AgentApp                  entry point: config → MCP → TUI → ConversationFactory
├── agent/                    provider-agnostic model factories, UsageTracker,
│   ├── AgentEvent            sealed events: the seam between agents and any UI
│   ├── ConversationBridge    one turn in, events out (StreamBridge = single, AgenticBridge = agentic)
│   └── ConversationFactory   rebuilds a bridge on /model, /thinking, /mcp, /agentic
├── agentic/                  langchain4j-agentic system
│   ├── Agents, FlinkAgents   @Agent interfaces; FlinkTypes = typed FlinkStatement / CriticResult
│   ├── AgentSystemFactory    supervisor + specialists + loop + presenter wiring, tool scopes, skills
│   └── UiAgentListener       AgentListener → AgentEvents
├── tools/McpBridge           MCP clients, scoped ToolProviders
└── tui/                      tui4j Elm model, views, theme, markdown, slash commands
```

## Demo

`demo/DEMO-SCRIPT.md` has the six-act script with per-act hand-off narration, timings and recovery
steps. `docs/agentic-migration-plan.md` is the architecture rationale, research and progress log
(TamboUI port pending). `docs/brewmaster-agent.md` is the original single-agent design, kept for history.

## Build targets

`./gradlew shadowJar` (fat JAR, `build/libs/kafka-agent.jar`), `./gradlew nativeCompile` (GraalVM,
best effort), `./gradlew jlinkImage`. Compilation uses `-parameters`, which `langchain4j-agentic`
needs to bind `@V` arguments by name.
