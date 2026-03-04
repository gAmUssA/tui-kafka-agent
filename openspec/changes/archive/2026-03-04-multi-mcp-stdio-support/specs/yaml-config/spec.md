## ADDED Requirements

### Requirement: MCP servers map config
`AppConfig` SHALL support `mcp.servers` as a map of named server configurations, where each entry specifies transport type and connection details.

#### Scenario: SSE server config
- **WHEN** config contains `mcp.servers.confluent` with `type: sse` and `url: http://localhost:8080/sse`
- **THEN** `appConfig.getMcpServers()` returns a map with key "confluent" containing an SSE server config

#### Scenario: Stdio server config
- **WHEN** config contains `mcp.servers.filesystem` with `type: stdio`, `command: ["npx", "-y", "..."]`, and optional `env` map
- **THEN** `appConfig.getMcpServers()` returns a map with key "filesystem" containing a stdio server config

#### Scenario: No servers configured
- **WHEN** `mcp.servers` is not present in config
- **THEN** `appConfig.getMcpServers()` returns an empty map

### Requirement: Backward-compatible auto-connect-url
`AppConfig` SHALL continue to support `mcp.auto-connect-url` as a deprecated shorthand, treating it as a single SSE server named "default".

#### Scenario: Legacy auto-connect-url
- **WHEN** config contains `mcp.auto-connect-url: http://localhost:8080/sse` but no `mcp.servers`
- **THEN** `appConfig.getMcpServers()` returns `{"default": {type: "sse", url: "http://localhost:8080/sse"}}`

#### Scenario: Both formats present
- **WHEN** config contains both `mcp.auto-connect-url` and `mcp.servers`
- **THEN** `mcp.servers` takes precedence and `auto-connect-url` is ignored

### Requirement: Default config documents server map
`default-config.yaml` SHALL include commented-out examples of the `mcp.servers` map with both SSE and stdio examples.

#### Scenario: Default config contains server examples
- **WHEN** `default-config.yaml` is read
- **THEN** it contains commented examples of `mcp.servers` with SSE and stdio entries
