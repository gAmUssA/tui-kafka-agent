## 1. Configuration Layer

- [ ] 1.1 Add `McpServerConfig` record to `config/` package — fields: `name`, `type` (enum: `sse`/`stdio`), `url`, `command` (List<String>), `env` (Map<String,String>)
- [ ] 1.2 Add `AppConfig.getMcpServers()` method returning `Map<String, McpServerConfig>` parsed from `mcp.servers` YAML map
- [ ] 1.3 Add backward-compat logic: if `mcp.auto-connect-url` is set but `mcp.servers` is absent, return a single "default" SSE server config with deprecation warning
- [ ] 1.4 Update `default-config.yaml` with commented-out `mcp.servers` examples (SSE and stdio)

## 2. McpBridge Refactor

- [ ] 2.1 Replace single `McpClient mcpClient` field with `Map<String, McpClient> clients` (LinkedHashMap for insertion order)
- [ ] 2.2 Add transport factory method `createTransport(McpServerConfig)` returning `HttpMcpTransport` or `StdioMcpTransport` based on config type
- [ ] 2.3 Refactor `connect(String url)` → `connect(String name, McpServerConfig config)` that adds to the clients map
- [ ] 2.4 Add convenience `connectSse(String name, String url)` for runtime `/mcp` command use
- [ ] 2.5 Rebuild `McpToolProvider` from all connected clients after each connect/disconnect
- [ ] 2.6 Add `disconnect(String name)` method — closes one client and rebuilds tool provider
- [ ] 2.7 Add `disconnectAll()` method — closes all clients
- [ ] 2.8 Add `getConnectedServers()` returning `Map<String, Integer>` (name → tool count)
- [ ] 2.9 Add `listToolNamesByServer()` returning `Map<String, List<String>>`
- [ ] 2.10 Update `close()` to iterate all clients

## 3. Startup & Agent Wiring

- [ ] 3.1 Update `AgentApp.main()` — iterate `config.getMcpServers()` and connect each server via `McpBridge.connect(name, config)`
- [ ] 3.2 Ensure `AgentFactory.create()` receives the aggregated tool provider from `McpBridge.getToolProvider()`

## 4. TUI Commands

- [ ] 4.1 Update `/mcp` handler: no args → list connected servers with type and tool count
- [ ] 4.2 Update `/mcp` handler: single URL arg → `connectSse("default", url)` (backward compat)
- [ ] 4.3 Update `/mcp` handler: `<name> <url>` → `connectSse(name, url)`
- [ ] 4.4 Add `/mcp disconnect [name]` handler — disconnect one or all servers
- [ ] 4.5 Update `/tools` handler — group tools by server name using `listToolNamesByServer()`
- [ ] 4.6 Rebuild agent after each connect/disconnect (already done for single server, ensure it works with aggregated provider)

## 5. Verification

- [ ] 5.1 Build with `./gradlew shadowJar` — ensure no compilation errors
- [ ] 5.2 Test SSE connection: start an MCP server, connect via `/mcp test http://localhost:8080/sse`, verify tools listed
- [ ] 5.3 Test stdio connection: configure a stdio server in config YAML, verify subprocess starts and tools discovered
- [ ] 5.4 Test multi-server: connect two servers, verify `/tools` shows grouped output, verify agent can use tools from both
- [ ] 5.5 Test disconnect: `/mcp disconnect <name>`, verify server removed and remaining tools still work
- [ ] 5.6 Test backward compat: use old `mcp.auto-connect-url` config, verify it still works with deprecation warning
