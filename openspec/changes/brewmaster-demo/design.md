## Context

kafka-agent is a working generic Kafka/Flink TUI agent. The brewmaster demo needs to rebrand it, inject domain-specific AI behavior, and add a few demo-lifecycle features — all without forking or breaking the generic use case. The existing architecture (Elm loop, streaming bridge, MCP integration, slash commands) handles 90% of what the demo needs. The gaps are: branding, system prompt, auto-MCP, a cleanup command, and alert coloring.

## Goals / Non-Goals

**Goals:**
- Same binary serves both generic kafka-agent and brewmaster demo via config
- Demo runs start-to-finish without manual `/mcp` command
- Presenter can reset and re-run the demo cleanly with `/reset-brewery`
- Alert severity in tool output is visually distinct (red/yellow)

**Non-Goals:**
- No separate build or project — single binary, config-driven
- No brewery-specific `@Tool` methods — Claude calls MCP tools via natural language
- No automated demo scripting — presenter types live

## Decisions

### D1: Config-driven profile (not compile-time flag)

Add `app.name`, `app.system-prompt`, and `mcp.auto-connect-url` fields to `config.yaml`. The brewmaster demo simply uses a config file with these set. No code branching — the same binary adapts.

**Why not a `--profile` CLI flag?** Config file is simpler, already works with env vars, and the presenter just sets it once. CLI flags add argument parsing complexity for no benefit in a demo context.

### D2: System prompt from config file

Move the system prompt from hardcoded string in `AgentFactory.SYSTEM_PROMPT` to a `app.system-prompt` config field. The default remains the generic Kafka assistant prompt. The brewmaster config overrides it with brewing + streaming expertise.

**Why not a prompt file?** One fewer file to manage. The YAML multi-line string (`|`) handles multi-paragraph prompts cleanly. If prompts grow very large, we can add `app.system-prompt-file` later.

### D3: Auto-MCP connect in AgentApp.main()

If `mcp.auto-connect-url` is set in config, connect to the MCP server before entering the TUI loop. This runs in `AgentApp.main()` after config load, before `Program.run()`. The `McpBridge` is passed to `AgentModel` pre-connected.

**Why not in init()?** MCP connection is blocking network I/O. Doing it before the TUI starts avoids a frozen UI. Connection failures print to stderr before alt-screen takes over — the presenter sees the error clearly.

### D4: `/reset-brewery` sends natural language to AI

Rather than hardcoding topic/statement deletion logic, `/reset-brewery` sends a message to the AI: "Delete all brewery-* topics and any running Flink statements related to the brewery pipeline." Claude uses the MCP tools to do the cleanup. This is simpler, more resilient to schema changes, and is itself a demo moment.

**Why not direct MCP calls?** The agent already has the tools. Letting it figure out what to clean up is more robust than maintaining a hardcoded list. Also showcases the agent's capability.

### D5: Regex-based severity coloring in tool output

Scan tool result text for `CRITICAL`, `WARNING`, `INFO` keywords and apply red/yellow/blue coloring inline. This happens in `ToolResultView` during rendering — no changes to the data model.

**Why not structured alert parsing?** Tool results are opaque strings from MCP. Parsing JSON would be fragile. Keyword highlighting is simple, works for the demo, and also helps with non-brewery tool output.

## Risks / Trade-offs

- **[System prompt too long]** → Brewing expertise adds ~200 tokens. With prompt caching enabled, this is paid once per conversation, not per turn. Acceptable.
- **[Auto-MCP fails silently]** → If the MCP server is unreachable at startup, print a clear error and continue without tools. The presenter can manually `/mcp` as fallback.
- **[/reset-brewery is AI-dependent]** → Claude might miss a topic or statement. Mitigation: the prompt explicitly lists what to clean up. If a reset fails, the presenter can re-run it or manually clean up.
- **[Severity regex false positives]** → Words like "CRITICAL" in normal text would get colored. In practice this is rare and harmless in a demo.

## Open Questions

- Should `app.name` affect the binary name / launcher script, or just the TUI header and welcome screen? (Propose: TUI-only, keep binary as `kafka-agent`)
