# Brewmaster Agent — Demo Script

**Total time**: ~7 minutes
**Target**: Vaadin Cruise, Jfokus, Stockholm & Milan meetups

## Pre-flight Checklist

- [ ] `ANTHROPIC_API_KEY` env var set
- [ ] `MCP_SERVER_URL` env var set (e.g., `https://mcp-confluent.ai-assisted.engineering/sse`)
- [ ] `~/.config/kafka-agent/config.yaml` copied from `docs/brewmaster-config.yaml`
- [ ] Terminal at least 120 chars wide, dark background
- [ ] Run `/reset` if topics exist from a previous run
- [ ] Verify no stale schemas: run `list-schemas` — `brewery-sensors-value` should not exist
- [ ] Verify `./kafka-agent` launches and shows "Brewmaster Agent" in header with tool count

---

## Act 1 — "Set Up the Brewery" (~1 min)

**Type** (or use `/setup`):
> I'm building a smart brewery monitoring system. Create three topics — brewery-sensors, brewery-alerts, and brewery-metrics. Then tag them all with a brewery-pipeline tag.

**What happens**: Agent calls `create-topics`, `create-topic-tags`, `add-tags-to-topic`. Explains the pipeline.

**Talking point**: "Notice the agent doesn't just execute — it explains what it built and why. Infrastructure as conversation."

---

## Act 2 — "Fill the Fermenters" (~1 min)

**Type:**
> I have 3 fermenters running — an IPA in fermenter-1, a Stout in fermenter-2, and a Pilsner in fermenter-3. Produce realistic sensor data for all of them.

**What happens**: First produce registers an Avro schema with Schema Registry. Subsequent calls reuse it. Agent produces ~30 messages with style-appropriate temperatures (IPA 18-20, Stout 20-22, Pilsner 10-12).

**Talking point**: "Notice it registered an Avro schema on the first message — every subsequent message is validated against that schema. No free-form JSON anymore."

---

## Act 3 — "Watch the Brewery" (~1.5 min)

**Type:**
> Create a Flink SQL job that monitors for temperature anomalies — alert if any fermenter drifts more than 2 degrees from its style's normal range in a 30-second window.

**What happens**: Agent writes Flink SQL with CASE logic for style-specific thresholds, submits it, verifies it's running.

**Talking point**: "Watch the SQL stream in token by token. Style-specific thresholds — it knows IPA is different from Pilsner. This is AI writing real-time stream processing."

---

## Act 4 — "Sabotage the IPA" (~1 min)

**Type:**
> Oh no — fermenter-1's cooling system just failed! Produce readings showing the temperature climbing fast — 22, 25, 28, 31 degrees.

**Wait for completion, then type:**
> Did the alerts fire?

**What happens**: Agent produces escalating readings, then consumes from `brewery-alerts` to show Flink caught the spike.

**Talking point**: "The pipeline works. Flink detected the anomaly in real-time and generated alerts. Let's see what the agent recommends."

---

## Act 5 — "Diagnose and Fix" (~1 min)

**Type:**
> What's the current state of all fermenters? What should we do about fermenter-1?

**What happens**: Agent reads latest data, diagnoses the IPA as critical, gives actual brewing advice (fusel alcohols, diacetyl rest, Belgian Saison pivot).

**Talking point**: "It's not just a data tool — it's a domain expert. The Belgian Saison suggestion is real brewing knowledge. This is what makes AI agents powerful: combining data access with expertise."

---

## Act 6 — "Schema Evolution" (~1.5 min)

**Type:**
> The brewery just installed humidity sensors. Show me the current schema, then evolve it to add humidity_percent, and verify the new version registered.

**What happens** (three beats):
1. Agent calls `list-schemas` — shows v1 schema (10 fields, all required)
2. Agent produces with an updated Avro schema adding `{"name": "humidity_percent", "type": "double", "default": 0.0}` — Schema Registry accepts the backward-compatible v2
3. Agent calls `list-schemas` again — shows v2 registered alongside v1

**Talking point**: "Version 1 to version 2, validated by Schema Registry, backward-compatible because of the default value. No downtime, no breaking consumers."

---

## Cleanup

After demo, type `/reset` to delete topics and Flink jobs for next run.

---

## If Things Go Wrong

| Problem                 | Recovery                                                                       |
|-------------------------|--------------------------------------------------------------------------------|
| MCP connection fails    | `/mcp <url>` to reconnect manually                                             |
| Flink SQL error         | Let the agent self-correct — this is actually a good demo moment               |
| Agent hallucinates tool | Say "That tool doesn't exist, use the available MCP tools"                     |
| Slow response           | Talk about prompt caching: "First turn is slower, subsequent turns are cached" |
| Topics already exist    | `/reset` then start over                                               |
