## 1. Config Layer — New Fields

- [x] 1.1 Add `getAppName()` to `AppConfig` — reads `app.name`, defaults to `"kafka-agent"`
- [x] 1.2 Add `getSystemPrompt()` to `AppConfig` — reads `app.system-prompt`, returns `null` if unset
- [x] 1.3 Add `getMcpAutoConnectUrl()` to `AppConfig` — reads `mcp.auto-connect-url`, defaults to `""`
- [x] 1.4 Update `default-config.yaml` with commented-out `app` and `mcp` sections

## 2. System Prompt from Config

- [x] 2.1 Modify `AgentFactory` to accept system prompt from `AppConfig.getSystemPrompt()`, falling back to the existing hardcoded prompt when `null`
- [x] 2.2 Create `brewmaster-config.yaml` example file in `docs/` with the full brewing + streaming system prompt

## 3. Auto-MCP Connection

- [x] 3.1 In `AgentApp.main()`, check `config.getMcpAutoConnectUrl()` — if non-empty, create `McpBridge` and connect before building the TUI
- [x] 3.2 Pass the pre-connected `McpBridge` to `AgentModel` (add `setMcpBridge()` method)
- [x] 3.3 Rebuild `AgentAssistant` with MCP tool provider if auto-connect succeeded

## 4. App Branding

- [x] 4.1 Pass `appConfig` to `AgentModel` so it can read `getAppName()`
- [x] 4.2 Update `HeaderView.render()` to accept app name parameter instead of hardcoded `"kafka-agent"`
- [x] 4.3 Update `renderWelcome()` in `AgentModel` to use `appConfig.getAppName()` in the title

## 5. /reset-brewery Command

- [x] 5.1 Add `"reset-brewery"` case to `handleSlashCommand()` in `AgentModel`
- [x] 5.2 Implement `handleResetBrewery()` — same pattern as `handleTopicsShortcut()`: check MCP, send cleanup message to AI via `streamBridge.sendMessage()`
- [x] 5.3 Add `/reset-brewery` to the `/help` output

## 6. Alert Severity Styling

- [x] 6.1 Add severity color styles to `Theme.java` — `SEVERITY_CRITICAL` (red), `SEVERITY_WARNING` (gold), `SEVERITY_INFO` (blue)
- [x] 6.2 Add `styleSeverity(String text)` utility method in `ToolResultView` that replaces `CRITICAL`/`WARNING`/`INFO` keywords with styled versions
- [x] 6.3 Apply `styleSeverity()` to tool result content before rendering inside the box

## 7. Demo Config & Docs

- [x] 7.1 Create `docs/brewmaster-config.yaml` — complete config file for the demo (app name, system prompt, auto-MCP URL)
- [x] 7.2 Create `demo/DEMO-SCRIPT.md` — 6-act script with timing, talking points, and pre-flight checklist

## 8. Build & Verify

- [x] 8.1 `./gradlew shadowJar` compiles clean
- [ ] 8.2 Run with default config — verify generic kafka-agent behavior unchanged
- [ ] 8.3 Run with brewmaster config — verify branding, auto-MCP, `/reset-brewery`, and severity coloring
