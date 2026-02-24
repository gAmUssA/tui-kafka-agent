## Context

LangChain4j's streaming supports tool use events. When the AI decides to call a tool, we get a tool call event before execution, and a tool result after. We need to surface these in the TUI.

## Goals / Non-Goals

**Goals:**
- Spinner animation during tool execution
- Tool results rendered as distinct visual blocks in chat

**Non-Goals:**
- No tool call confirmation/approval — tools execute automatically
- No parallel tool execution display

## Decisions

**tui4j SpinnerModel**: Use the built-in spinner with a tick command. Show `⠋ Calling <toolName>()...` text.

**ToolExecutingMessage and ToolCompleteMessage**: Two new message types. ToolExecuting starts the spinner, ToolComplete stops it and renders the result.

**Tool results as ChatEntry with TOOL role**: Tool results get their own ChatEntry so they appear in the chat history with distinct rendering.

## Risks / Trade-offs

- [Spinner tick may conflict with streaming tokens] → Both are just messages in the event loop; they interleave naturally
