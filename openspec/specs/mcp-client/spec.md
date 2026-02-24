## ADDED Requirements

### Requirement: Connect to MCP server
`McpBridge` SHALL connect to an MCP server via HTTP SSE transport given a URL, initialize the client, and discover available tools.

#### Scenario: Successful connection
- **WHEN** `/mcp https://example.com/mcp` is executed
- **THEN** the MCP client connects, discovers tools, and a confirmation message shows the number of tools loaded

#### Scenario: Connection failure
- **WHEN** the MCP server URL is unreachable
- **THEN** an error message is displayed in the chat viewport

### Requirement: Dynamic tool loading
MCP-discovered tools SHALL be added to the AI agent's tool set and the AI service rebuilt.

#### Scenario: MCP tools available to AI
- **WHEN** an MCP server provides 3 tools
- **THEN** the AI can call those tools in subsequent conversations

### Requirement: /mcp slash command
`/mcp <url>` SHALL trigger MCP server connection via McpBridge.

#### Scenario: Command triggers connection
- **WHEN** user types "/mcp https://example.com/mcp"
- **THEN** McpBridge.connect() is called with the URL
