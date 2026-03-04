## ADDED Requirements

### Requirement: Multiple simultaneous MCP connections
`McpBridge` SHALL manage multiple named MCP client connections simultaneously, keyed by a unique server name string.

#### Scenario: Connect two servers
- **WHEN** two MCP servers are connected with names "confluent" and "filesystem"
- **THEN** `McpBridge.getConnectedServers()` returns both names and `McpBridge.isConnected()` returns true

#### Scenario: Independent lifecycle
- **WHEN** server "filesystem" is disconnected
- **THEN** server "confluent" remains connected and its tools are still available

### Requirement: Aggregated tool provider
`McpBridge` SHALL produce a single `McpToolProvider` that aggregates tools from all connected MCP clients.

#### Scenario: Tools from multiple servers
- **WHEN** server "confluent" provides 5 tools and server "filesystem" provides 3 tools
- **THEN** `McpBridge.getToolProvider()` returns a provider exposing all 8 tools

#### Scenario: Provider rebuilt on connect/disconnect
- **WHEN** a new server is connected or an existing server is disconnected
- **THEN** the `McpToolProvider` is rebuilt with the current set of connected clients

### Requirement: Server-grouped tool listing
`McpBridge.listToolNamesByServer()` SHALL return a map of server name to tool name list.

#### Scenario: Grouped output
- **WHEN** two servers are connected with different tools
- **THEN** `listToolNamesByServer()` returns `{"confluent": ["list-topics", ...], "filesystem": ["read_file", ...]}`

### Requirement: Connect named SSE server at runtime
`/mcp <name> <url>` SHALL connect an SSE-transport MCP server with the given name.

#### Scenario: Runtime SSE connection
- **WHEN** user types `/mcp myserver http://localhost:8080/sse`
- **THEN** an SSE MCP client named "myserver" is connected and tools are discovered

#### Scenario: Backward-compatible single-arg form
- **WHEN** user types `/mcp http://localhost:8080/sse` (URL only, no name)
- **THEN** the server is connected with the auto-generated name "default"

### Requirement: Disconnect server
`/mcp disconnect <name>` SHALL close the named MCP client and remove it from the active set.

#### Scenario: Disconnect specific server
- **WHEN** user types `/mcp disconnect confluent`
- **THEN** the "confluent" client is closed, its tools are removed, and the agent is rebuilt without them

#### Scenario: Disconnect all
- **WHEN** user types `/mcp disconnect`
- **THEN** all MCP clients are closed and the agent is rebuilt with no tools

### Requirement: List connected servers
`/mcp` with no arguments SHALL display all connected servers with their transport type and tool count.

#### Scenario: Show server status
- **WHEN** user types `/mcp` with two servers connected
- **THEN** output shows each server name, transport type, and number of tools
