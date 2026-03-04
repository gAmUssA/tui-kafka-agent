## MODIFIED Requirements

### Requirement: Connect to MCP server
`McpBridge` SHALL connect to MCP servers via HTTP SSE or stdio transport, supporting multiple simultaneous connections keyed by server name.

#### Scenario: Successful SSE connection
- **WHEN** `/mcp myserver https://example.com/mcp` is executed
- **THEN** the MCP client connects via SSE transport, discovers tools, and a confirmation message shows the server name and number of tools loaded

#### Scenario: Successful stdio connection (via config)
- **WHEN** a server with `type: stdio` is configured and auto-connected at startup
- **THEN** the MCP client connects via stdio transport and discovers tools

#### Scenario: Connection failure
- **WHEN** the MCP server URL is unreachable
- **THEN** an error message is displayed and other connected servers are unaffected

### Requirement: Dynamic tool loading
MCP-discovered tools from all connected servers SHALL be aggregated into a single tool provider and the AI service rebuilt.

#### Scenario: MCP tools available to AI
- **WHEN** two MCP servers provide 3 and 5 tools respectively
- **THEN** the AI can call all 8 tools in subsequent conversations

### Requirement: /mcp slash command
`/mcp` SHALL support multiple subcommands: no args (list servers), `<name> <url>` (connect SSE), `<url>` (connect SSE as "default"), `disconnect [name]` (disconnect).

#### Scenario: List connected servers
- **WHEN** user types "/mcp" with servers connected
- **THEN** a list of connected servers with tool counts is shown

#### Scenario: Connect with name
- **WHEN** user types "/mcp myserver https://example.com/mcp"
- **THEN** McpBridge connects an SSE server named "myserver"

#### Scenario: Disconnect
- **WHEN** user types "/mcp disconnect myserver"
- **THEN** the "myserver" client is closed and tools refreshed

## REMOVED Requirements

### Requirement: /mcp slash command
**Reason**: Replaced by updated multi-form `/mcp` command above
**Migration**: `/mcp <url>` still works (backward compatible, uses name "default")
