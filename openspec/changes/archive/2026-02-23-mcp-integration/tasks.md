## 1. Dependencies

- [x] 1.1 Add `dev.langchain4j:langchain4j-mcp` dependency to build.gradle.kts

## 2. MCP Client

- [x] 2.1 Create `McpBridge.java` with `connect(String url)` method
- [x] 2.2 Create MCP transport (HttpMcpTransport) and client (DefaultMcpClient)
- [x] 2.3 Call `mcpClient.initialize()` and discover tools via `McpToolProvider`
- [x] 2.4 Handle connection errors gracefully — return error string, don't throw

## 3. Integration

- [x] 3.1 Implement `/mcp` slash command handler that calls McpBridge.connect()
- [x] 3.2 Rebuild AiServices with combined tool set (built-in + MCP tools)
- [x] 3.3 Update header bar to show MCP connection status
- [x] 3.4 Verify: connect to an MCP server, ask AI to use its tools
