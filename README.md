# kafka-agent / Brewmaster Agent

A terminal AI agent for Apache Kafka and Flink on Confluent Cloud. It talks to the
[Confluent MCP server](https://github.com/confluentinc/mcp-confluent) for tools, runs on
[LangChain4j](https://docs.langchain4j.dev) 1.20 with the `langchain4j-agentic` multi-agent
module, and renders a three-panel terminal UI with TamboUI. The bundled demo profile is the
**Brewmaster Agent**: a brewery IoT pipeline built, monitored and sabotaged through conversation.

## Run

### Prerequisites

| Need | Notes |
|---|---|
| Java 21 | `./gradlew shadowJar` builds `build/libs/kafka-agent.jar`; `./kafka-agent` launches it (needs a real TTY) |
| `ANTHROPIC_API_KEY` | Workers can run on Ollama, the supervisor planner always uses Anthropic |
| Confluent Cloud | A Kafka cluster, Schema Registry and a Flink compute pool the MCP server is configured for |
| [Confluent MCP server](https://github.com/confluentinc/mcp-confluent) 1.6+ | Must serve **Streamable HTTP** (`server.transports: [http]`, endpoint `/mcp`). Its native Kafka module needs **Node 22**. The old `/sse` transport is no longer supported by the LangChain4j client |
| Optional: [tessl](https://tessl.io) | `tessl install` restores the agent rules referenced from `AGENTS.md` (`.tessl/` is not committed) |

### Environment variables

The config files substitute `${VAR}` from the environment; unset variables resolve to empty.

| Variable | Used for |
|---|---|
| `ANTHROPIC_API_KEY` | Anthropic models |
| `FLINK_CATALOG_NAME`, `FLINK_DATABASE_NAME` | Flink catalog = environment **display name**, database = Kafka cluster **display name**. Given to the Flink agents so `DESCRIBE` works first time; set the same two in the MCP server's `.env` |
| `CONFLUENT_BOOTSTRAP`, `CONFLUENT_API_KEY`, `CONFLUENT_API_SECRET`, `FLINK_ENV_ID`, `CONFLUENT_ORG_ID` | Referenced by the config template for future direct-client use; the MCP server holds the real credentials |
| `KAFKA_AGENT_LOG`, `KAFKA_AGENT_LOG_LEVEL` | Log file path and level (default `~/.config/kafka-agent/kafka-agent.log`, `warn`) |

### Start

```bash
# 1. Confluent MCP server, from its checkout, on Node 22 (e.g. `nvm use 22`)
node dist/index.js -c config.yaml -e .env      # config.yaml: server.transports: [http]

# 2. Agent config: copy docs/brewmaster-config.yaml to ~/.config/kafka-agent/config.yaml
#    (or symlink it) and point mcp.servers.confluent.url at http://localhost:8080/mcp

# 3. Build and run in a real terminal
./gradlew shadowJar
./kafka-agent
```

The header shows `app │ provider:model │ mode │ MCP: N tools`. `mode` is `agentic` or `single`.

The TamboUI front end has three panels: **Chat** (you + presenter) │ **Activity** (hand-offs, tool calls, one row each;
Enter opens the detail overlay, Esc closes) · **Prompt**. Tab cycles panes.

Library logs go to `~/.config/kafka-agent/kafka-agent.log` (override with `KAFKA_AGENT_LOG`), at WARN unless
`KAFKA_AGENT_LOG_LEVEL=info|debug` — never to the terminal the UI is painting.

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
Hand-offs and tool calls appear live in the Activity pane.

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

Keys: `Enter` send, `Tab` cycle Prompt → Chat → Activity, arrows select an Activity row, `Enter` on it opens the detail overlay, `Esc` closes,
`PgUp`/`PgDn` or mouse wheel scroll, `Ctrl+C` quit.

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
├── tui2/BreweryApp           TamboUI three-panel front end
└── tui/                      slash-command parser and registry
```

## Demo

`demo/DEMO-SCRIPT.md` has the six-act script with per-act hand-off narration, timings and recovery
steps. `docs/agentic-migration-plan.md` is the architecture rationale, research and progress log
`docs/brewmaster-agent.md` is the original single-agent design, kept for history.

## Build targets

`./gradlew shadowJar` (fat JAR, `build/libs/kafka-agent.jar`), `./gradlew nativeCompile` (GraalVM,
best effort), `./gradlew jlinkImage`. Compilation uses `-parameters`, which `langchain4j-agentic`
needs to bind `@V` arguments by name.

## License

Code is [MIT](LICENSE). Bundled third-party content keeps its own license: the
`confluent-cloud-flink-sql` skill under `src/main/resources/skills/` is Apache-2.0
(`skills/LICENSE-confluent-agent-skills`), the `flink-sql` skill comes from the
`gamussa/flink-sql` Tessl tile; see `skills/SOURCES.md`.
