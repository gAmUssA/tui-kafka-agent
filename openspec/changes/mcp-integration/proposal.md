## Why

MCP (Model Context Protocol) lets the agent connect to external tool servers at runtime. Users can extend the agent's capabilities by connecting to any MCP-compatible server via the `/mcp` command.

## What Changes

- Add LangChain4j MCP client dependency
- Create `McpBridge` class for connecting to MCP servers and loading tools
- Implement `/mcp <url>` slash command to connect at runtime
- Dynamically add MCP-provided tools to the AI service

## Non-goals

- No MCP server discovery — user provides the URL
- No persistent MCP connections across restarts
- No MCP server hosting (this app is a client only)

## Capabilities

### New Capabilities
- `mcp-client`: MCP client connection and dynamic tool loading via LangChain4j McpToolProvider

### Modified Capabilities
