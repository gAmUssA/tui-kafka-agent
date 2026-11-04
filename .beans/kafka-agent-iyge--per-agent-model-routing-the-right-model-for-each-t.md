---
# kafka-agent-iyge
title: 'Per-agent model routing: the right model for each task'
status: draft
type: feature
priority: normal
created_at: 2026-11-04T21:57:21Z
updated_at: 2026-11-04T21:57:21Z
---

Idea for the next presentation (not this talk): a truly mixed multi-agent system, with a
model per agent alongside the existing tools-per-agent scoping. The point: context and tools
matter more than one big model; spend premium tokens only where they pay off.

## Evidence from the 2026-11-04 rehearsals
- Haiku 4.5: all six acts pass, ~2x faster and ~1/4 of Sonnet's cost; 0 rejected produce calls.
- Sonnet 4.6: all six acts pass; strongest on Flink SQL authoring (Haiku's first statement can fail to parse).
- DeepSeek V4.1 Flash (Fireworks): cheapest (~1/10 of Sonnet); fails only on produce-message,
  whose schema LangChain4j strips (langchain4j#6981). Untested on tool-free roles.

## Starting assignment to test
| Agent | Candidate model | Why |
|---|---|---|
| brewmaster (no tools) | DeepSeek V4.1 Flash | domain reasoning only; avoids the broken tool schema |
| confluent (tool-heavy) | Haiku 4.5 | clean tool calls, fast |
| sqlAuthor / sqlCritic | Sonnet 4.6 (or an OpenAI model) | the important bit: SQL correctness |
| sqlSubmitter, flinkOps | Haiku 4.5 | short tool sequences |
| supervisor (planner) | Haiku 4.5 or Sonnet 4.6 | routing quality vs latency, measure both |
| presenter (no tools, streams) | Haiku 4.5 or DeepSeek | narration only |

## Todo
- [ ] Config shape: `agentic.models.<agent>: <provider>:<model>`, defaulting to the active `/model`
- [ ] Build each agent's ChatModel from its own entry (AgentSystemFactory); share one UsageTracker
- [ ] Per-agent cost/latency in /usage and the Activity pane (show which model ran each hand-off)
- [ ] Header and /report show the model mix
- [ ] Decide what `/model <alias>` does in mixed mode (override all, or switch a named profile)
- [ ] Rehearse the six acts with the mix; compare against all-Sonnet and all-Haiku runs
- [ ] Optional: typed `produce_reading` tool so DeepSeek can take the confluent role too
- [ ] Slide: per-agent model table with measured time and cost
