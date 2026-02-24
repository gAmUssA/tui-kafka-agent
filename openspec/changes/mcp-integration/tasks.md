## 1. Dependencies

- [ ] 1.1 Add `dev.langchain4j:langchain4j-mcp` dependency to build.gradle.kts

## 2. MCP Client

- [ ] 2.1 Create `McpBridge.java` with `connect(String url)` method
- [ ] 2.2 Create MCP transport (HttpMcpTransport) and client (DefaultMcpClient)
- [ ] 2.3 Call `mcpClient.initialize()` and discover tools via `McpToolProvider`
- [ ] 2.4 Handle connection errors gracefully — return error string, don't throw

## 3. Integration

- [ ] 3.1 Implement `/mcp` slash command handler that calls McpBridge.connect()
- [ ] 3.2 Rebuild AiServices with combined tool set (built-in + MCP tools)
- [ ] 3.3 Update header bar to show MCP connection status
- [ ] 3.4 Verify: connect to an MCP server, ask AI to use its tools
