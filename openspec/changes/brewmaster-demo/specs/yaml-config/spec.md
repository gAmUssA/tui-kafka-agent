## ADDED Requirements

### Requirement: App name config field
`AppConfig` SHALL support `app.name` as an optional string field, defaulting to `"kafka-agent"`.

#### Scenario: App name getter
- **WHEN** `app.name` is set to `"Brewmaster Agent"` in config
- **THEN** `appConfig.getAppName()` returns `"Brewmaster Agent"`

#### Scenario: App name default
- **WHEN** `app.name` is not set in config
- **THEN** `appConfig.getAppName()` returns `"kafka-agent"`

### Requirement: System prompt config field
`AppConfig` SHALL support `app.system-prompt` as an optional multi-line string field. When not set, it SHALL return `null` (signaling the caller to use its built-in default).

#### Scenario: Custom system prompt
- **WHEN** `app.system-prompt` is set to a multi-line YAML string
- **THEN** `appConfig.getSystemPrompt()` returns the full prompt text

#### Scenario: No system prompt configured
- **WHEN** `app.system-prompt` is not set
- **THEN** `appConfig.getSystemPrompt()` returns `null`

### Requirement: Auto-MCP URL config field
`AppConfig` SHALL support `mcp.auto-connect-url` as an optional string field for automatic MCP server connection on startup.

#### Scenario: Auto-connect URL set
- **WHEN** `mcp.auto-connect-url` is set to `"http://localhost:8080/sse"`
- **THEN** `appConfig.getMcpAutoConnectUrl()` returns `"http://localhost:8080/sse"`

#### Scenario: Auto-connect URL not set
- **WHEN** `mcp.auto-connect-url` is not set
- **THEN** `appConfig.getMcpAutoConnectUrl()` returns `""` (empty string)

### Requirement: Default config documents new fields
`default-config.yaml` SHALL include commented-out examples of `app.name`, `app.system-prompt`, and `mcp.auto-connect-url`.

#### Scenario: Default config is parseable
- **WHEN** `default-config.yaml` is parsed
- **THEN** it contains the `app` and `mcp` sections with example values
