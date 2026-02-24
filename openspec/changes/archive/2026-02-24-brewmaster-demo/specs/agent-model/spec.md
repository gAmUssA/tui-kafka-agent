## MODIFIED Requirements

### Requirement: Basic view rendering
`view()` SHALL return a string showing the app name from config in the header. The welcome screen SHALL display the configured app name.

#### Scenario: Initial view with default config
- **WHEN** `view()` is called after init with default config
- **THEN** the returned string contains "kafka-agent" in the header

#### Scenario: Initial view with custom app name
- **WHEN** `view()` is called after init with `app.name` set to "Brewmaster Agent"
- **THEN** the returned string contains "Brewmaster Agent" in the header and welcome box

## ADDED Requirements

### Requirement: Auto-MCP connection indicator
When MCP is auto-connected at startup, the header SHALL show the tool count immediately on first render.

#### Scenario: Auto-connected MCP
- **WHEN** `AgentModel` receives a pre-connected `McpBridge` from `AgentApp`
- **THEN** the header shows the MCP tool count from the first frame

### Requirement: Handle /reset-brewery command
`AgentModel` SHALL handle the `/reset-brewery` slash command by sending a cleanup message to the AI (same pattern as `/topics`).

#### Scenario: Reset brewery
- **WHEN** user types `/reset-brewery` and MCP is connected
- **THEN** a cleanup message is sent to the AI via `streamBridge.sendMessage()`
- **AND** the AI uses MCP tools to delete brewery topics and Flink statements
