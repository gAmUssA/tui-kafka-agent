## Why

When the AI calls a tool, the user needs visual feedback: a spinner while executing and a styled result box when complete. Without this, tool calls appear as silent pauses.

## What Changes

- Add tui4j `SpinnerModel` that shows during tool execution
- Define `ToolExecutingMessage` and `ToolCompleteMessage` custom messages
- Render tool results in bordered boxes within the chat viewport
- Show tool name in spinner text: "Calling listTopics()..."

## Non-goals

- No collapsible/expandable tool results yet — always shown
- No tool result caching

## Capabilities

### New Capabilities
- `tool-spinner`: Spinner indicator during tool execution with tool name display
- `tool-result-display`: Bordered box rendering for tool results in chat viewport

### Modified Capabilities
