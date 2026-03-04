## Context

The agent currently has a single-server MCP architecture: `McpBridge` holds one `McpClient` connected via `HttpMcpTransport`. The `/mcp <url>` command replaces any existing connection. Configuration supports only `mcp.auto-connect-url` (a single URL string).

LangChain4j's `McpToolProvider.builder().mcpClients(...)` already accepts a varargs/list of `McpClient` instances, making multi-server aggregation straightforward. LangChain4j MCP (`1.11.0-beta19`) also ships `StdioMcpTransport` for subprocess-based servers — no new dependencies needed.

## Goals / Non-Goals

**Goals:**
- Connect to multiple named MCP servers simultaneously
- Support both SSE (HTTP) and stdio (subprocess) transports
- Aggregate tools from all connected servers into a single `McpToolProvider`
- Configure servers declaratively in YAML with auto-connect on startup
- Manage connections at runtime via `/mcp` commands

**Non-Goals:**
- Auto-reconnection or health monitoring
- WebSocket transport support
- Dynamic tool refresh after initial connection
- MCP resources/prompts protocol support

## Decisions

### 1. McpBridge → multi-client map

**Decision**: Refactor `McpBridge` to hold a `Map<String, McpClient>` keyed by server name. Rebuild the `McpToolProvider` from all connected clients whenever a server is added or removed.

**Why**: Keeps the single `ToolProvider` interface that `AgentFactory` already consumes. `McpToolProvider.builder().mcpClients(List)` handles tool namespace aggregation.

**Alternative considered**: Separate `McpBridge` per server, compose tool providers externally. Rejected — adds complexity without benefit since `McpToolProvider` already merges clients.

### 2. Config structure: `mcp.servers` map

**Decision**: Replace `mcp.auto-connect-url` with a `mcp.servers` map:

```yaml
mcp:
  servers:
    confluent:
      type: sse
      url: http://localhost:8080/sse
    filesystem:
      type: stdio
      command: ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
      env:
        DEBUG: "true"
```

**Why**: Each server needs a name (for display and disconnect), a transport type, and type-specific config. A map keyed by name is the natural structure.

**Migration**: If `mcp.auto-connect-url` is present (old format), treat it as a single SSE server named `default`. Log a deprecation warning.

### 3. Transport factory method

**Decision**: Add a static `McpBridge.createTransport(ServerConfig)` method that returns the appropriate `McpTransport` based on config type (`sse` → `HttpMcpTransport`, `stdio` → `StdioMcpTransport`).

**Why**: Isolates transport creation logic. Easy to extend later (e.g., add `streamable-http` type for `StreamableHttpMcpTransport`).

### 4. `/mcp` command changes

**Decision**:
- `/mcp` (no args) → list all connected servers with tool counts
- `/mcp <name> <url>` → connect an SSE server with given name
- `/mcp disconnect <name>` → disconnect a specific server
- `/mcp disconnect` → disconnect all servers

**Why**: The name parameter enables managing multiple connections. SSE is the most common runtime-connected type (stdio servers are typically pre-configured in YAML since they need command arrays).

### 5. Tool display grouping

**Decision**: `/tools` output groups tools by server name: `[confluent] list-topics, produce-message` etc.

**Why**: When multiple servers provide tools, users need to know which server owns which tool for debugging.

## Risks / Trade-offs

- **[Breaking config]** `mcp.auto-connect-url` users must update config → Mitigated by backward-compat fallback with deprecation warning
- **[Startup latency]** Multiple servers connect sequentially at startup → Acceptable for typical 1-3 servers; could parallelize later if needed
- **[Stdio process lifecycle]** Subprocess MCP servers must be cleaned up on exit → `McpBridge.close()` already called from `AgentModel.cleanup()`; will iterate all clients
- **[Tool name collisions]** Two servers may expose tools with same name → LangChain4j `McpToolProvider` uses client key prefix to disambiguate
