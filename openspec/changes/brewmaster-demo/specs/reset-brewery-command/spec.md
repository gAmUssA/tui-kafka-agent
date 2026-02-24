## ADDED Requirements

### Requirement: /reset-brewery command
`/reset-brewery` SHALL send a cleanup request to the AI agent that instructs it to delete all brewery-related topics and Flink statements using the connected MCP tools.

#### Scenario: Reset with MCP connected
- **WHEN** user types `/reset-brewery` and MCP tools are available
- **THEN** a message is sent to the AI: "Delete all brewery-* topics (brewery-sensors, brewery-alerts, brewery-metrics) and delete any running Flink statements related to the brewery pipeline. Also remove any brewery-pipeline tags. Confirm what was deleted."
- **AND** the AI uses MCP tools to perform the cleanup and reports results

#### Scenario: Reset without MCP
- **WHEN** user types `/reset-brewery` and no MCP server is connected
- **THEN** an error message is shown: "No MCP server connected. Use /mcp <url> first."

### Requirement: /reset-brewery appears in /help
The `/reset-brewery` command SHALL be listed in the `/help` output with description "Reset demo (delete brewery topics & Flink jobs)".

#### Scenario: Help includes reset-brewery
- **WHEN** user types `/help`
- **THEN** the output includes `/reset-brewery` with its description
