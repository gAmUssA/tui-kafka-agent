## Why

kafka-agent needs to be conference-ready as the "Brewmaster Agent" demo for Vaadin Cruise, Jfokus, and meetups (Feb 2026). The current generic Kafka assistant must transform into a domain-specific brewery monitoring demo that tells a 6-act story: setup topics, produce sensor data, create Flink anomaly detection, sabotage a fermenter, diagnose the problem, and evolve the schema — all through natural conversation with Claude.

## What Changes

- Add a **brewmaster system prompt** with brewing + streaming domain expertise, injected based on config profile
- Add **auto-MCP connect on startup** so the demo doesn't require a manual `/mcp` command
- Add **`/reset-brewery` slash command** that deletes demo topics and Flink statements for clean re-runs
- Add **alert severity coloring** in tool results (CRITICAL = red, WARNING = yellow) for the anomaly detection acts
- Add **configurable app branding** (name in header/welcome screen) so the same binary can present as "Brewmaster Agent"
- Add a **demo script document** with timing, talking points, and pre-flight checklist

## Non-goals

- Not building a separate project — this extends kafka-agent with a brewmaster profile
- Not adding brewery-specific `@Tool` methods — Claude calls MCP tools directly
- Not automating the demo — presenter types natural language, agent responds live

## Capabilities

### New Capabilities
- `brewmaster-profile`: Config-driven profile that sets system prompt, app name, and auto-MCP connection for the brewery demo
- `reset-brewery-command`: `/reset-brewery` slash command that cleans up demo topics + Flink statements via AI
- `alert-severity-styling`: Color-coded rendering of CRITICAL/WARNING/INFO severity strings in tool output

### Modified Capabilities
- `yaml-config`: Add profile support, auto-MCP URL, and app branding fields
- `builtin-commands`: Register the new `/reset-brewery` command
- `agent-model`: Use branding from config in header/welcome, trigger auto-MCP connect in init

## Impact

- **Config**: New fields in `AppConfig` and `default-config.yaml`
- **System prompt**: `AgentFactory` reads prompt from config instead of hardcoded string
- **TUI**: `AgentModel` header/welcome use configurable app name; `ChatView`/`ToolResultView` detect severity keywords
- **Commands**: `CommandParser` + `AgentModel` handle `/reset-brewery`
- **Docs**: New `demo/DEMO-SCRIPT.md` for presenters
