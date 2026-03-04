## ADDED Requirements

### Requirement: Stdio MCP transport support
`McpBridge` SHALL support connecting to MCP servers via stdio transport, spawning the server as a subprocess.

#### Scenario: Connect stdio server
- **WHEN** a server config specifies `type: stdio` with `command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"]`
- **THEN** `McpBridge` creates a `StdioMcpTransport` with the given command and connects successfully

#### Scenario: Stdio with environment variables
- **WHEN** a stdio server config includes `env: {"API_KEY": "value123"}`
- **THEN** the spawned subprocess receives those environment variables

### Requirement: Stdio process cleanup
All stdio MCP subprocess servers SHALL be terminated when `McpBridge.close()` is called.

#### Scenario: Clean shutdown
- **WHEN** the application exits and `McpBridge.close()` is called
- **THEN** all stdio subprocess servers are terminated via `McpClient.close()`

#### Scenario: Individual disconnect
- **WHEN** a single stdio server is disconnected via `/mcp disconnect <name>`
- **THEN** only that server's subprocess is terminated

### Requirement: Transport type factory
`McpBridge` SHALL determine the transport type from configuration and create the appropriate `McpTransport` implementation.

#### Scenario: SSE type creates HttpMcpTransport
- **WHEN** server config has `type: sse` and `url: http://localhost:8080/sse`
- **THEN** an `HttpMcpTransport` is created with the given URL

#### Scenario: Stdio type creates StdioMcpTransport
- **WHEN** server config has `type: stdio` and `command: ["node", "server.js"]`
- **THEN** a `StdioMcpTransport` is created with the given command
