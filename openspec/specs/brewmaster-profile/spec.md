### Requirement: Configurable system prompt
`AgentFactory` SHALL read the system prompt from `AppConfig.getSystemPrompt()` instead of a hardcoded string. The default prompt SHALL be the existing generic Kafka assistant prompt.

#### Scenario: Default prompt (no config override)
- **WHEN** `app.system-prompt` is not set in config
- **THEN** `AgentFactory` uses the built-in generic Kafka/Flink assistant prompt

#### Scenario: Custom brewmaster prompt
- **WHEN** `app.system-prompt` is set to a multi-line brewing + streaming expertise prompt
- **THEN** `AgentFactory` uses that prompt for all AI service instances

### Requirement: Configurable app name
`AppConfig` SHALL provide `getAppName()` returning the value of `app.name`, defaulting to `"kafka-agent"`.

#### Scenario: Default app name
- **WHEN** `app.name` is not set in config
- **THEN** `getAppName()` returns `"kafka-agent"`

#### Scenario: Custom app name
- **WHEN** `app.name` is set to `"Brewmaster Agent"`
- **THEN** `getAppName()` returns `"Brewmaster Agent"`

### Requirement: Auto-MCP connection on startup
When `mcp.auto-connect-url` is set in config, `AgentApp.main()` SHALL connect to the MCP server before starting the TUI loop. The connected `McpBridge` SHALL be passed to `AgentModel`.

#### Scenario: Auto-connect succeeds
- **WHEN** `mcp.auto-connect-url` is set and the server is reachable
- **THEN** the TUI starts with MCP tools already available and the header shows the tool count

#### Scenario: Auto-connect fails
- **WHEN** `mcp.auto-connect-url` is set but the server is unreachable
- **THEN** an error is printed to stderr, and the TUI starts without MCP tools (user can retry with `/mcp`)

#### Scenario: No auto-connect configured
- **WHEN** `mcp.auto-connect-url` is not set
- **THEN** the app starts as before — no MCP connection until `/mcp` is used
