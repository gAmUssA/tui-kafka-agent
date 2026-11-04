# Stage cheat sheet — Brewmaster Agent

One page. The full script with narration is `DEMO-SCRIPT.md`.

## Pre-flight (10 minutes before)

1. Charger plugged in; `caffeinate -dis &`
2. `tmux kill-server` (no stale keys in the multiplexer)
3. mcp-confluent, from `~/projects/ai/mcp-confluent`:
   `~/.nvm/versions/node/v22.23.4/bin/node dist/index.js -c config.yaml -e .env`
4. Export `FLINK_CATALOG_NAME=current-2026-demo FLINK_DATABASE_NAME=brewmaster`, then launch
   `opwith anthropic ./kafka-agent` (approve 1Password)
5. In the app: `/reset`, then **restart mcp-confluent** (step 3) and `/mcp confluent http://127.0.0.1:8080/mcp`
6. `/model haiku`. Header shows `anthropic:haiku │ agentic │ MCP: 73 tools`
7. Terminal at 24 pt; check from the back of the room

## The six prompts

1. `I'm building a smart brewery monitoring system. Create three topics — brewery-sensors, brewery-alerts, and brewery-metrics. Then tag them all with a brewery-pipeline tag.`
2. `I have 3 fermenters running — an IPA in fermenter-1, a Stout in fermenter-2, and a Pilsner in fermenter-3. Produce realistic sensor data for all of them.`
3. `Create a Flink SQL job that alerts on every sensor reading where a fermenter's temperature is more than 2 degrees above its style's normal range — no windows, one alert per reading. Write alerts to brewery-alerts.`
4. `Oh no — fermenter-1's cooling system just failed! Produce readings showing the temperature climbing fast — 22, 25, 28, 31 degrees. Then produce one normal reading each for fermenter-2 and fermenter-3.`
   Then wait ~20 s and ask: `Did the alerts fire?`
5. `What's the current state of all fermenters? What should we do about fermenter-1?`
6. `The brewery just installed humidity sensors. Show me the current schema, then evolve it to add humidity_percent, and verify the new version registered.`

Closer: `/report` (agent topology and timeline in the browser; a snapshot, rerun for later turns).

## Numbers for the slide (rehearsed 2026-11-04, same app, same tools)

| Model                           | Acts 1–6  | Cost        | Result                                       |
|---------------------------------|-----------|-------------|----------------------------------------------|
| Claude Haiku 4.5                | ~8–10 min | ~$0.75–0.90 | all six acts pass                            |
| Claude Sonnet 4.6               | ~18 min   | ~$3.30      | all six acts pass                            |
| DeepSeek V4.1 Flash (Fireworks) | ~18 min   | ~$0.33      | fails from Act 2: 178 rejected produce calls |

Story: the smallest Claude ran the whole demo about 2× faster for about a quarter of the cost. It
failed the night before, and what fixed it was tooling, not a bigger model:
- A stale schema cache in the MCP server (filed as confluentinc/mcp-confluent#753)
- A tool-name hint instead of a crash
- A guard so a read-only question can't delete the alert job
- A prompt that asks for the SQL that actually alerts

DeepSeek still fails because the tool's schema reaches it broken: LangChain4j drops
`additionalProperties: true` (filed as langchain4j/langchain4j#6981). Claude reads past it;
DeepSeek takes it literally. Same app, same prompt: the tool contract decides.

## Recovery lines

| What you see | Say / type |
|---|---|
| Act 1: "brewery-pipeline registered as brewery_pipeline" | Expected: Confluent rejects hyphens in tag names. "It read the error and adapted." |
| Act 3: first statement FAILED, second RUNNING | "And that is why there is a critic / submitter loop." |
| Act 4: "Did the alerts fire?" says no or only some | `produce one more normal reading for each fermenter`, wait 20 s, ask again |
| Act 4: no alerts at all and the SQL parses `val` as JSON | Schema missing: the mcp-confluent restart after `/reset` was skipped. Narrate, move on to Act 5 |
| `brewops: Failed to invoke agent method` | Check `tail ~/.config/kafka-agent/kafka-agent.log`; tools may already have run: ask "is the brewery alert statement running?" |
| A turn runs away | No cancel: `/quit`, relaunch (MCP server keeps running) |
| Haiku stumbles on Flink | `/model sonnet`, re-ask Act 3. The header switch is part of the story |

## After the talk

`/reset` → restart mcp-confluent → `/mcp confluent http://127.0.0.1:8080/mcp`.
