## Why

The agent currently supports only a single MCP server connection via HTTP SSE transport. Real-world usage requires connecting to multiple MCP servers simultaneously (e.g., Confluent Cloud MCP + a local filesystem MCP) and supporting stdio-based MCP servers (subprocess-spawned servers like `npx @modelcontextprotocol/server-*`). This unlocks the same multi-server workflow that Claude Code and other AI agents support.

## What Changes

- **McpBridge** supports managing multiple named MCP clients simultaneously, aggregating their tools into a single `McpToolProvider`
- **Stdio transport** support added alongside existing HTTP SSE transport
- **Config format** changes from single `mcp.auto-connect-url` string to a `mcp.servers` map (keyed by name), each entry specifying transport type (`sse` or `stdio`), URL or command, and optional env vars
- `/mcp` command updated: `/mcp` with no args lists connected servers; `/mcp <name> <url>` connects an SSE server; `/mcp disconnect <name>` disconnects one
- `/tools` output groups tools by server name
- **BREAKING**: `mcp.auto-connect-url` config key replaced by `mcp.servers` map

## Capabilities

### New Capabilities
- `multi-mcp`: Support for connecting to multiple MCP servers simultaneously with aggregated tool discovery
- `stdio-transport`: Support for stdio-based MCP server transport (subprocess-spawned servers)

### Modified Capabilities
- `mcp-client`: Connection lifecycle changes from single-server to multi-server management
- `yaml-config`: New `mcp.servers` config structure replaces `mcp.auto-connect-url`

## Impact

- **McpBridge.java**: Major refactor — single client → map of named clients
- **AppConfig.java**: New `getMcpServers()` getter returning structured server configs
- **AgentModel.java**: `/mcp` and `/tools` command handlers updated
- **AgentApp.java**: Startup auto-connect iterates over configured servers
- **default-config.yaml**: New `mcp.servers` section with examples
- **build.gradle.kts**: No new dependencies (LangChain4j MCP already includes `StdioMcpTransport`)

## Non-goals

- MCP server health monitoring / auto-reconnection
- Dynamic tool refresh (hot-reload when server adds/removes tools)
- WebSocket MCP transport support
- MCP resource or prompt protocol support (tools only)
