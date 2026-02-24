## Why

Tool results appear before the agent's explanatory text in chat history because `ToolCompleteMessage` commits to `chatHistory` immediately, while the agent's streaming response is only committed on `StreamCompleteMessage`. Additionally, multi-line agent responses lose their left indent and ANSI styling across `\n` boundaries because `Style.render()` wraps the entire string in a single escape sequence.

## What Changes

- Buffer tool results in `AgentModel` during streaming instead of committing them immediately to `chatHistory`; flush them after the agent's response text is committed
- Show buffered tool results in the transient streaming area so users see them in real-time while the agent is still responding
- Render multi-line chat content line-by-line in `ChatView`, applying `Style.render()` per line with consistent `"  "` indent so ANSI codes don't span newlines
- Apply the same line-by-line fix to the streaming partial response in `refreshViewport()`

## Non-goals

- Changing the `StreamBridge` message lifecycle or LangChain4j callback ordering
- Modifying `ToolResultView` box rendering (it already handles its own layout)
- Adding markdown parsing or rich text rendering to agent responses

## Capabilities

### New Capabilities

- `chat-message-ordering`: Tool results are buffered during streaming and flushed after agent text, ensuring natural `[You] → [Agent] → [Tool]` display order
- `multiline-chat-rendering`: Multi-line content in all chat entry types is rendered line-by-line with per-line ANSI styling and consistent left indent

### Modified Capabilities

- `chat-viewport`: ChatEntry rendering now uses line-by-line styling; badge labels appear on their own line above content
- `tool-result-display`: Tool results are buffered during streaming and committed to history after agent response instead of immediately
- `agent-model`: New `pendingToolResults` buffer; modified handlers for `ToolCompleteMessage`, `StreamCompleteMessage`, and `ErrorMessage`

## Impact

- **Files**: `AgentModel.java`, `ChatView.java`
- **Visual**: Badge labels move to their own line; content lines are indented below. Tool result boxes appear after agent text instead of before.
- **Dependencies**: None changed
