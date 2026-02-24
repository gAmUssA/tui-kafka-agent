## Context

LangChain4j has built-in MCP support via `McpToolProvider`. MCP servers expose tools over HTTP SSE transport that can be dynamically added to the AI agent.

## Goals / Non-Goals

**Goals:**
- Connect to an MCP server via URL
- Load discovered tools into the agent
- Rebuild AI service with new tool set

**Non-Goals:**
- No stdio MCP transport — HTTP SSE only
- No MCP resource or prompt support — tools only

## Decisions

**LangChain4j McpToolProvider**: Use the built-in integration rather than raw MCP client. It handles tool discovery and conversion to LangChain4j ToolSpecification.

**Rebuild AI service on connect**: When new MCP tools are loaded, rebuild the AiServices instance with the combined tool set (built-in + MCP). Chat memory is preserved.

**Connection status in header**: Show MCP connection state in the header bar after connecting.

## Risks / Trade-offs

- [MCP server may be unavailable] → Show clear error in chat, don't crash
- [Tool name collisions between built-in and MCP] → MCP tools take precedence; warn in chat
