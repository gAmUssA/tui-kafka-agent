# Brewmaster Agent — Demo Script

**Total time**: ~10–12 minutes in agentic mode (Acts 1–3 measured; 4–6 estimated — time them at rehearsal and update)
**Target**: Vaadin Cruise, Jfokus, Stockholm & Milan meetups
**Mode**: multi-agent (`langchain4j-agentic`) — see "The team" below. `/agentic off` gives the original single-agent behaviour.

## Pre-flight Checklist

- [ ] `ANTHROPIC_API_KEY` env var set
- [ ] Confluent MCP server running with the HTTP transport (`server.transports: [http, sse]` in its `config.yaml`, endpoint `http://127.0.0.1:8080/mcp`). Its native Kafka module needs Node 22 — from `~/projects/ai/mcp-confluent`: `~/.nvm/versions/node/v22.22.2/bin/node dist/index.js -c config.yaml -e .env`. The old `/sse` endpoint is no longer supported by the Java client.
- [ ] `~/.config/kafka-agent/config.yaml` is (a symlink to) `docs/brewmaster-config.yaml` — check `agentic.enabled: true`, `anthropic.model: claude-sonnet-4-6`, `mcp.servers.confluent.url: http://localhost:8080/mcp`, and `confluent.flink.catalog-name` / `database-name` = the environment and cluster **display names** (Flink catalogs are not IDs); without them every DESCRIBE fails once and the Flink agents go hunting
- [ ] In the MCP server's `.env`, set `FLINK_CATALOG_NAME` / `FLINK_DATABASE_NAME` to the same display names so the server's own default resolves too
- [ ] `./gradlew shadowJar` after pulling — the launcher only builds if the JAR is missing
- [ ] Terminal at least 120 chars wide, dark background
- [ ] Run `/reset` if topics exist from a previous run (it routes to `flinkOps` for statements and `confluent` for topics, tags, schemas)
- [ ] Verify no stale schemas: ask "list the brewery schemas" — `brewery-sensors-value` should not exist
- [ ] Verify no stale statements: ask "list the running brewery Flink statements" — none should remain
- [ ] Verify `./kafka-agent` launches and shows "Brewmaster Agent │ anthropic:sonnet │ agentic │ MCP: 58 tools" in the header
- [ ] Run `/agentic` to confirm multi-agent mode is ON (set by `agentic.enabled: true` in the config); `/agentic off` switches to the single assistant for the "before/after" contrast

## The team (agentic mode)

One turn = supervisor (`brewops`) plans → specialists work → `presenter` streams the answer. Hand-offs show up as `↳ brewmaster done in 18 s` trace lines and tool boxes; Ctrl+O expands a box to show the call arguments (for `create-flink-statement`, that is the real SQL).

| Specialist | Owns | Tools |
|---|---|---|
| `brewmaster` | brewing science: readings, thresholds, diagnosis | none |
| `confluent` | topics, tags, Schema Registry, produce/consume | Kafka + SR tools |
| `flink` | new statements: `sqlAuthor` → `sqlCritic` loop → `sqlSubmitter` | DESCRIBE + submit tools, `flink-sql` skill |
| `flinkOps` | existing statements: status, health, exceptions, delete | Flink ops tools |

Budget per act in agentic mode (measured): a read question ≈ $0.06 / 30 s; Act 3 ≈ $0.45 / 2.5 min. Narrate the trace while it runs.

---

## Act 1 — "Set Up the Brewery" (~1 min)

**Type** (or use `/setup`):
> I'm building a smart brewery monitoring system. Create three topics — brewery-sensors, brewery-alerts, and brewery-metrics. Then tag them all with a brewery-pipeline tag.

**What happens**: `brewops` routes straight to `confluent`, which calls `create-topics`, `create-topic-tags`, `add-tags-to-topic`; `presenter` explains the pipeline.

**Talking point**: "The supervisor read the request, decided this is platform work, and handed it to the Confluent specialist — the brewmaster never woke up. Infrastructure as conversation, with the right specialist on the job."

---

## Act 2 — "Fill the Fermenters" (~1 min)

**Type:**
> I have 3 fermenters running — an IPA in fermenter-1, a Stout in fermenter-2, and a Pilsner in fermenter-3. Produce realistic sensor data for all of them.

**What happens**: Two hand-offs. `brewmaster` generates ~30 readings as JSON lines (IPA 18-20, Stout 20-22, Pilsner 10-12 — it knows lager yeast is cold). Then `confluent` produces them; the first produce registers the Avro schema, the rest reuse it.

**Talking point**: "Watch the trace: brewmaster first, confluent second. The brewing knowledge and the platform work are different agents with different tools — the brewmaster literally cannot touch Kafka. And the first message registered an Avro schema; every message after it is validated."

---

## Act 3 — "Watch the Brewery" (~2.5 min)

**Type:**
> Create a Flink SQL job that monitors for temperature anomalies — alert if any fermenter drifts more than 2 degrees above its style's normal range in a 10-second window. Write alerts to brewery-alerts.

**What happens** (this is the money act — narrate the trace):
1. `brewmaster` turns the ask into thresholds per style and a window length.
2. `sqlAuthor` activates the `flink-sql` skill (`activate_skill`, `read_skill_resource`), runs `describe-flink-table` on `brewery-sensors` and `brewery-alerts`, and drafts a typed statement — trace shows `→ statement brewery-temp-alerts drafted`.
3. `sqlCritic` re-DESCRIBEs and returns a typed verdict — trace shows `→ PASS` (or `→ FAIL — …` and the loop goes round again, max 3).
4. `sqlSubmitter` calls `create-flink-statement` and polls `check-flink-statement-health` until RUNNING.
5. `presenter` streams the outcome with the SQL verbatim. Ctrl+O on the `create-flink-statement()` box shows the exact submitted SQL.

**Talking point**: "Author, critic, submitter. The critic doesn't return a score or a paragraph — it returns a typed object, and the loop branches on `successful == true`. That's a compile-checked decision, not string parsing. And the author didn't have 60 lines of Flink rules in its prompt: it activated a skill and read the Confluent reference when it needed it."

**If the critic fails once**: that is the best possible outcome — say "and that is why there is a critic" and let it go round.

---

## Act 4 — "Sabotage the IPA" (~1 min)

**Type:**
> Oh no — fermenter-1's cooling system just failed! Produce readings showing the temperature climbing fast — 22, 25, 28, 31 degrees.

**Wait for completion, then type:**
> Did the alerts fire?

**What happens**: First turn — `brewmaster` writes the escalating fermenter-1 readings (22 → 25 → 28 → 31 °C, ~10 s apart), `confluent` produces them. Second turn — `brewops` routes to `confluent`, which consumes `brewery-alerts`; the presenter shows the alerts Flink generated (IPA threshold is 22 °C, so all four readings should trip it once the 10-second windows close).

**Talking point**: "The pipeline works. Flink detected the anomaly in real time and wrote alerts — the agent only read them back. Let's see what the brewmaster makes of it."

**Timing note**: give Flink 20–30 s between the produce turn and "Did the alerts fire?" — window close plus the statement's checkpoint. If the consume comes back empty, ask again.

---

## Act 5 — "Diagnose and Fix" (~1 min)

**Type:**
> What's the current state of all fermenters? What should we do about fermenter-1?

**What happens**: Two hand-offs in the other order from Act 2 — `confluent` consumes the latest `brewery-sensors` (and alerts), then `brewmaster` diagnoses: Stout and Pilsner nominal, IPA critical, fusel alcohols above 25 °C, diacetyl rest if cooling is back within 30 minutes, otherwise repitch as a Belgian Saison.

**Talking point**: "Same team, opposite direction: the platform specialist fetched the data, the brewmaster interpreted it. The Belgian Saison pivot is real brewing knowledge — and it came from an agent that has no tools at all."

---

## Act 6 — "Schema Evolution" (~1.5 min)

**Type:**
> `The brewery just installed humidity sensors. Show me the current schema, then evolve it to add humidity_percent, and verify the new version registered.`

**What happens** (three beats, all inside one `confluent` task; the brewmaster may be asked first for a realistic humidity reading):
1. `confluent` calls `list-schemas` — shows v1 schema (10 fields, all required)
2. `confluent` produces with an updated Avro schema adding `{"name": "humidity_percent", "type": "double", "default": 0.0}` — Schema Registry accepts the backward-compatible v2
3. `confluent` calls `list-schemas` again — shows v2 registered alongside v1

**Note**: the Flink statement from Act 3 keeps running through the schema change — mention it.

**Talking point**: "Version 1 to version 2, validated by Schema Registry, backward-compatible because of the default value. No downtime, no breaking consumers."

---

## Closer — "Show the topology" (~30 s)

**Type:** `/report`

**What happens**: writes the `AgentMonitor` HTML report (topology + timeline + token counts for every turn of the session) and opens it in the browser.

**Talking point**: "Every hand-off you just watched, with timings and tokens. One line of code: `HtmlReportGenerator.generateReport(monitor, path)`."

**Optional contrast**: `/agentic off`, re-ask Act 1's question, `/agentic on`. Same tools, one prompt, no hand-offs — the audience sees what the supervisor buys and what it costs.

## Cleanup

After demo, type `/reset` to delete topics and Flink jobs for next run (`brewops` routes it to `flinkOps` for the statements and `confluent` for topics, tags and schemas).

---

## If Things Go Wrong

| Problem                 | Recovery                                                                       |
|-------------------------|--------------------------------------------------------------------------------|
| MCP connection fails    | `/mcp <url>` to reconnect manually                                             |
| Flink SQL error         | Let the agent self-correct — this is actually a good demo moment               |
| Agent hallucinates tool | Say "That tool doesn't exist, use the available MCP tools"                     |
| Supervisor routes wrong  | Re-ask naming the specialist ("ask flinkOps to…"). The authoring workflow refuses non-authoring tasks (returns NOT-APPLICABLE), so a mis-routed delete no longer creates a statement. Worst case `/agentic off` and continue single-mode |
| A turn runs away        | There is no cancel yet: Enter is ignored while a turn runs. `/quit` (or Ctrl+C) kills it; relaunch — the MCP server keeps running |
| Critic loops 3× and fails | Ask `flinkOps` for the statement exceptions, or say "submit it anyway" — the story survives |
| Slow response           | Talk about prompt caching: "First turn is slower, subsequent turns are cached" |
| Topics already exist    | `/reset` then start over                                                       |
