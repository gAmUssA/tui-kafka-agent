# 🍺 Brewmaster Agent — Implementation Plan

> **Elevator pitch**: "An AI agent in your terminal that builds a smart brewery monitoring pipeline from scratch, produces sensor data, detects anomalies with Flink SQL, and saves the IPA — all through conversation."

## Overview

A terminal-based AI agent built with **LangChain4j** (Anthropic Claude) + **tui4j** (Bubble Tea for Java) that talks to **Confluent Cloud** via the Confluent MCP server. The demo tells a story in 6 acts: setup → fill → monitor → sabotage → diagnose → evolve.

**Conference targets**: Vaadin Cruise, Jfokus, Stockholm & Milan meetups (Feb 2026)

---

## Tech Stack

| Layer | Technology | Role |
|---|---|---|
| TUI | tui4j 0.2.5 (Bubble Tea for Java) | Terminal UI framework — Elm Architecture |
| AI | LangChain4j 1.9.1 + Anthropic Claude | Streaming chat, tool calling, memory |
| Backend | Confluent MCP Server | 24 tools: topics, produce, consume, Flink SQL, schemas, tags, connectors |
| Flink | Confluent Cloud Flink SQL | Real-time anomaly detection & aggregation |
| Java | 21+ (virtual threads) | Async tool execution |

---

## Available Confluent MCP Tools (24 total)

These are the actual tools available through the connected MCP server — the agent calls them directly.

**Topic Management** (7)
- `list-topics`, `create-topics`, `delete-topics`
- `search-topics-by-name`, `search-topics-by-tag`
- `get-topic-config`, `alter-topic-config`

**Message I/O** (2)
- `consume-messages` — auto-deserializes via Schema Registry (AVRO, JSON, PROTOBUF)
- `produce-message` — auto-serializes via Schema Registry

**Schema Registry** (1)
- `list-schemas` — all subjects with full schema definitions

**Flink SQL** (4)
- `create-flink-statement`, `read-flink-statement`, `list-flink-statements`, `delete-flink-statements`

**Governance / Tags** (5)
- `list-tags`, `create-topic-tags`, `add-tags-to-topic`, `remove-tag-from-entity`, `delete-tag`

**Connectors** (4)
- `list-connectors`, `create-connector`, `read-connector`, `delete-connector`

**Infrastructure** (3)
- `list-environments`, `read-environment`, `list-clusters`

---

## Data Model

### Topic: `brewery-sensors` — raw IoT readings

```json
{
  "sensor_id": "fermenter-3",
  "batch_id": "IPA-2026-02",
  "beer_style": "West Coast IPA",
  "temperature_c": 19.2,
  "pressure_bar": 1.03,
  "ph": 4.12,
  "dissolved_o2_ppm": 0.08,
  "gravity": 1.045,
  "stage": "primary_fermentation",
  "timestamp": 1708700400000
}
```

Each beer style has different normal ranges (agent knows this):
- **IPA**: 18–20°C, pH 4.0–4.3, gravity starts ~1.065 → finishes ~1.012
- **Stout**: 20–22°C, pH 4.1–4.4, gravity starts ~1.070 → finishes ~1.015
- **Pilsner**: 10–12°C (lager yeast!), pH 4.2–4.5, gravity starts ~1.048 → finishes ~1.010

### Topic: `brewery-alerts` — Flink-generated anomaly events

```json
{
  "sensor_id": "fermenter-3",
  "batch_id": "IPA-2026-02",
  "alert_type": "TEMPERATURE_SPIKE",
  "severity": "CRITICAL",
  "message": "Temperature rose 4.9°C in 60 seconds — risk of fusel alcohol production",
  "current_value": 24.8,
  "threshold": 20.0,
  "window_start": "2026-02-23T14:30:00",
  "window_end": "2026-02-23T14:31:00"
}
```

### Topic: `brewery-metrics` — windowed aggregates per batch

```json
{
  "batch_id": "IPA-2026-02",
  "avg_temp_c": 19.4,
  "max_temp_c": 20.1,
  "min_ph": 4.08,
  "max_pressure_bar": 1.12,
  "avg_gravity": 1.042,
  "reading_count": 47,
  "window_start": "2026-02-23T14:30:00",
  "window_end": "2026-02-23T14:31:00"
}
```

---

## Demo Script — 6 Acts

### Act 1 — "Set Up the Brewery" (~1 min)

**You say**: *"I'm building a smart brewery monitoring system. Set up the topics and tag them."*

**Agent does**:
1. `create-topics` → `brewery-sensors`, `brewery-alerts`, `brewery-metrics`
2. `create-topic-tags` → creates `brewery-pipeline` tag definition
3. `add-tags-to-topic` → tags all three topics
4. Explains the pipeline architecture it just built

**Audience sees**: Topics appearing, tags being applied, agent explaining its reasoning. Clean infrastructure-as-conversation.

**MCP tools used**: `create-topics`, `create-topic-tags`, `add-tags-to-topic`

---

### Act 2 — "Fill the Fermenters" (~1 min)

**You say**: *"I have 3 fermenters running — an IPA in fermenter-1, a Stout in fermenter-2, and a Pilsner in fermenter-3. Produce realistic sensor data for all of them."*

**Agent does**:
1. Generates ~30 sensor readings with style-appropriate values:
    - IPA @ 18–20°C (ale yeast)
    - Stout @ 20–22°C (higher temp yeast)
    - Pilsner @ 10–12°C (lager yeast — the agent knows this!)
2. Calls `produce-message` for each reading
3. Summarizes what it produced

**Audience sees**: Messages being produced one by one, agent demonstrating domain knowledge about brewing temperatures. This is the first "Claude moment" — it doesn't just produce random data, it knows beer.

**MCP tools used**: `produce-message` (×30)

---

### Act 3 — "Watch the Brewery" (~1.5 min)

**You say**: *"Create a Flink SQL job that monitors for temperature anomalies — alert if any fermenter drifts more than 2 degrees from its style's normal range in a 30-second window."*

**Agent does**:
1. Writes Flink SQL with:
    - Windowed aggregation over `brewery-sensors`
    - CASE logic for style-specific thresholds
    - INSERT INTO `brewery-alerts`
2. Calls `create-flink-statement` to submit
3. Calls `read-flink-statement` to verify it's running
4. Explains the SQL it wrote

**Example SQL the agent might write**:
```sql
INSERT INTO `brewery-alerts`
SELECT
    sensor_id,
    batch_id,
    'TEMPERATURE_ANOMALY' AS alert_type,
    CASE
        WHEN ABS(temperature_c - expected_temp) > 4.0 THEN 'CRITICAL'
        WHEN ABS(temperature_c - expected_temp) > 2.0 THEN 'WARNING'
    END AS severity,
    CONCAT('Temperature ', CAST(temperature_c AS STRING), '°C deviates from expected ',
           CAST(expected_temp AS STRING), '°C for ', beer_style) AS message,
    temperature_c AS current_value,
    expected_temp AS threshold,
    window_start,
    window_end
FROM (
    SELECT *,
        CASE beer_style
            WHEN 'West Coast IPA' THEN 19.0
            WHEN 'Stout' THEN 21.0
            WHEN 'Pilsner' THEN 11.0
            ELSE 18.0
        END AS expected_temp
    FROM TABLE(
        TUMBLE(TABLE `brewery-sensors`, DESCRIPTOR(`$rowtime`), INTERVAL '30' SECOND)
    )
)
WHERE ABS(temperature_c - expected_temp) > 2.0
```

**Audience sees**: Flink SQL being written token-by-token (streaming!), then submitted and confirmed running. The style-specific thresholds show the agent reasoning about the domain.

**MCP tools used**: `create-flink-statement`, `read-flink-statement`

---

### Act 4 — "Sabotage the IPA" 🔥 (~1 min)

**You say**: *"Oh no — fermenter-1's cooling system just failed! Produce readings showing the temperature climbing fast."*

**Agent does**:
1. Produces a sequence of escalating readings for fermenter-1:
    - 20.1°C → 22.3°C → 24.8°C → 27.1°C → 29.5°C
2. Each message shows in the TUI as it's produced
3. Waits a moment, then:

**You say**: *"Did the alerts fire?"*

**Agent does**:
1. Calls `consume-messages` on `brewery-alerts`
2. Shows the anomaly alerts that Flink generated
3. Confirms the pipeline caught the spike

**Audience sees**: Temperature rising in real-time, then the "aha" moment — alerts appearing on the output topic. The pipeline works. Applause moment.

**MCP tools used**: `produce-message` (×5), `consume-messages`

---

### Act 5 — "Diagnose and Fix" (~1 min)

**You say**: *"What's the current state of all fermenters? What should we do about fermenter-1?"*

**Agent does**:
1. Calls `consume-messages` on `brewery-sensors` to get latest readings from all 3 fermenters
2. Summarizes the state: Stout and Pilsner nominal, IPA critical
3. Gives actual brewing advice:
    - "Fermenter-1 hit 29.5°C. Above 25°C, ale yeast produces fusel alcohols — harsh, solvent-like off-flavors."
    - "If cooling is restored within 30 minutes, the IPA may still be salvageable with a diacetyl rest."
    - "If it was above 25°C for more than an hour, consider repitching the batch as a Belgian Saison — that yeast strain thrives at 25–35°C."
4. Optionally produces a "cooling restored" reading to close the loop

**Audience sees**: Agent acting as both a streaming data expert AND a brewmaster. The Belgian Saison pivot is the laugh line. Shows Claude's ability to combine domain knowledge with real-time data analysis.

**MCP tools used**: `consume-messages`, `produce-message`

---

### Act 6 — "Schema Evolution" (~1 min)

**You say**: *"The brewery just installed humidity sensors. Add a humidity_percent field to the sensor data and produce some readings with the new field."*

**Agent does**:
1. Calls `list-schemas` to see current schema for `brewery-sensors-value`
2. Acknowledges the current schema
3. Produces new messages that include `humidity_percent: 72.5` alongside all existing fields
4. Explains schema compatibility — JSON Schema with Schema Registry allows additive fields
5. Calls `list-schemas` again to show the schema was updated
6. Verifies the Flink job still works with the new field (backward compatible)

**Audience sees**: Live schema evolution without breaking the pipeline. The agent handles it conversationally — no manual schema registry edits, no downtime. Shows the practical power of schema management.

**MCP tools used**: `list-schemas`, `produce-message` (×3-5), `list-schemas`

---

## TUI Layout

```
┌──────────────────────────────────────────────────┐
│ 🍺 Brewmaster Agent │ claude-sonnet │ 24 tools    │  ← header
├──────────────────────────────────────────────────┤
│                                                  │
│  You: Set up a brewery monitoring pipeline       │
│                                                  │
│  Agent: I'll create three topics for your        │
│  brewery pipeline...                             │
│                                                  │
│  ⠋ Calling create-topics...                      │  ← spinner
│                                                  │
│  ┌─ Tool: create-topics ──────────────────────┐  │
│  │ ✓ brewery-sensors                          │  │
│  │ ✓ brewery-alerts                           │  │
│  │ ✓ brewery-metrics                          │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  Agent: Done! I've created three topics:         │
│  • brewery-sensors for raw IoT readings          │
│  • brewery-alerts for anomaly detections         │
│  • brewery-metrics for aggregated stats          │
│  Now let's tag them for governance...            │
│                                                  │
├──────────────────────────────────────────────────┤
│ > Produce sensor data for 3 fermenters...    [⏎] │  ← input
└──────────────────────────────────────────────────┘
```

---

## Architecture

```
┌──────────────────────────────────────────┐
│             TUI Layer (tui4j)            │
│  Model ←→ Update ←→ View                │
│  TextInput | Viewport | Spinner | List   │
└──────────────┬───────────────────────────┘
               │ Custom Messages
               │ (StreamToken, ToolExec, Error)
┌──────────────▼───────────────────────────┐
│         Agent Layer (LangChain4j)        │
│  AnthropicStreamingChatModel             │
│  AI Service + @Tool + ChatMemory         │
│  System Prompt: "You are a brewmaster    │
│  and streaming data expert..."           │
└──────────────┬───────────────────────────┘
               │ Tool Calls (auto-dispatched)
┌──────────────▼───────────────────────────┐
│       Confluent MCP Server               │
│  24 tools: topics, produce, consume,     │
│  Flink SQL, schemas, tags, connectors    │
└──────────────────────────────────────────┘
```

---

## Anthropic Claude Configuration

```java
// Streaming model — token-by-token delivery to TUI
StreamingChatModel streamingModel = AnthropicStreamingChatModel.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .modelName("claude-sonnet-4-20250514")
    .maxTokens(4096)
    .temperature(0.7)
    .cacheSystemMessages(true)   // cache system prompt (brewing + streaming expertise)
    .cacheTools(true)            // cache tool definitions across turns
    .build();

// System prompt (cached after first call — fast subsequent turns)
String systemPrompt = """
    You are the Brewmaster Agent — an expert in both craft brewing AND
    streaming data architecture. You help users build and monitor brewery
    IoT pipelines using Kafka and Flink on Confluent Cloud.

    Brewing knowledge:
    - You know fermentation temperature ranges for ale, lager, and hybrid styles
    - You understand how temperature affects yeast behavior and beer quality
    - You can diagnose problems and suggest corrective actions

    Streaming knowledge:
    - You build Kafka topic architectures
    - You write Flink SQL for windowed aggregations and anomaly detection
    - You understand schema evolution and compatibility

    When producing sensor data, generate realistic values appropriate for
    each beer style. When writing Flink SQL, use proper windowing and
    handle NULL values. Always explain what you're doing and why.
    """;
```

---


### The Brewmaster Demo Flow

**Goal**: All 6 acts work end-to-end.

- System prompt with brewing + streaming expertise
- Test the full demo flow: create → produce → Flink SQL → sabotage → diagnose → evolve
- Handle edge cases: Flink SQL errors (agent self-corrects), empty topic reads, schema conflicts
- `/thinking` toggle for the diagnosis act (extended thinking)

**Deliverable**: Complete demo runnable start-to-finish.

### Phase 4: UI Polish & Slash Commands (Days 7–8)

**Goal**: Conference-ready UX.

- Header bar: model name, tool count, connection status
- Slash commands: `/clear`, `/model`, `/thinking`, `/help`, `/reset-brewery`
- Color-coded severity in alerts (WARNING = yellow, CRITICAL = red)
- Keyboard: Ctrl+C to cancel generation, Page Up/Down in viewport
- `/reset-brewery` command to clean up demo topics for re-run

**Deliverable**: Polished, demo-ready TUI.


## Configuration

```yaml
# ~/.config/brewmaster/config.yaml
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
  mcp-server-url: https://mcp-confluent.ai-assisted.engineering/sse
  environment-id: env-j03r6m
  cluster-id: lkc-2oyy82
  flink:
    compute-pool-id: lfcp-xxxxx    # needs to be in same region as cluster
    base-url: https://flink.us-east-2.aws.confluent.cloud
```

---

## File Structure

```
brewmaster-agent/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/example/brewmaster/
│   ├── BrewmasterApp.java            # main entry point
│   ├── tui/
│   │   ├── BrewmasterModel.java      # main tui4j Model (Elm Architecture)
│   │   ├── ChatEntry.java            # chat message record (role, content, timestamp)
│   │   ├── messages/                  # custom Message types for event loop
│   │   │   ├── StreamTokenMsg.java
│   │   │   ├── StreamCompleteMsg.java
│   │   │   ├── ToolExecutingMsg.java
│   │   │   ├── ToolCompleteMsg.java
│   │   │   └── ErrorMsg.java
│   │   ├── views/                     # view rendering helpers
│   │   │   ├── ChatView.java         # renders chat history
│   │   │   ├── HeaderView.java       # top bar: model, tools, status
│   │   │   ├── ToolResultView.java   # styled box for tool results
│   │   │   └── AlertView.java        # color-coded severity rendering
│   │   └── styles/
│   │       └── Theme.java            # lipgloss styles: colors, borders
│   ├── agent/
│   │   ├── BrewmasterService.java    # LangChain4j AI Service interface
│   │   ├── AnthropicConfig.java      # model builder, system prompt
│   │   ├── ModelSwitcher.java        # swap sonnet/haiku/opus at runtime
│   │   └── StreamBridge.java         # bridges TokenStream → tui4j Messages
│   ├── tools/
│   │   ├── BreweryTools.java         # @Tool wrappers around Confluent MCP
│   │   └── ConfluentMcpClient.java   # MCP server connection management
│   └── config/
│       └── AppConfig.java            # YAML config loader
├── src/main/resources/
│   └── default-config.yaml
└── demo/
    └── DEMO-SCRIPT.md                # timing + talking points for conference
```

---

## Key Technical Decisions

| Decision         | Choice                               | Rationale                                                                     |
|------------------|--------------------------------------|-------------------------------------------------------------------------------|
| Streaming bridge | Custom Messages + `Program.send()`   | tui4j's Elm loop requires messages; streaming tokens arrive on another thread |
| LLM              | Anthropic Claude Sonnet 4            | Best tool calling; prompt caching for fast repeated turns                     |
| Prompt caching   | `cacheSystemMessages` + `cacheTools` | System prompt + tool defs cached server-side → cheaper, faster                |
| Flink SQL        | Agent writes it, human approves      | Shows AI capability while keeping human in the loop                           |
| Data domain      | IoT Brewery                          | Memorable, relatable, intuitive anomaly detection story                       |
| Schema evolution | JSON Schema (not AVRO)               | Additive fields "just work" — simpler demo                                    |

## Risks & Mitigations

| Risk                                | Mitigation                                                                         |
|-------------------------------------|------------------------------------------------------------------------------------|
| Flink SQL syntax errors             | Agent sees error message, self-corrects — this is actually a good demo moment      |
| Flink compute pool in wrong region  | Pre-flight check; document setup; have backup non-Flink version of Acts 3-4        |
| tui4j viewport rendering issues     | Fall back to raw string; viewport scrolling confirmed working in Brief             |
| MCP server latency                  | Buffer tool results; show spinner; Anthropic prompt caching helps subsequent turns |
| Claude generates bad brewing advice | Reviewed system prompt; Claude's brewing knowledge is solid; worst case it's funny |
| Demo re-run needs cleanup           | `/reset-brewery` command deletes topics + Flink statements                         |

## Enterprise Pattern Mapping

For the conference talk — connect brewery concepts to what the audience builds at work:

| Brewery Demo | Enterprise Reality |
|---|---|
| Fermenter sensors → `brewery-sensors` | IoT / telemetry / event ingest |
| Style-specific thresholds | Multi-tenant monitoring with per-customer SLAs |
| Flink anomaly detection | Real-time alerting & fraud detection |
| Temperature spike → alert | Incident detection & escalation |
| "What should we do?" diagnosis | AI-assisted incident response (AIOps) |
| Schema evolution (add humidity) | Evolving event schemas without downtime |
| Topic tagging | Data governance & catalog |
| The Belgian Saison pivot | Graceful degradation 😄 |