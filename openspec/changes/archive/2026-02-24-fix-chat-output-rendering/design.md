## Context

The TUI chat app uses the Elm Architecture via tui4j. Messages flow through `AgentModel.update()` which processes `ToolCompleteMessage`, `StreamTokenMessage`, and `StreamCompleteMessage` in the order LangChain4j's `TokenStream` fires them. The LangChain4j lifecycle is: `beforeToolExecution` → `onToolExecuted` → `onPartialResponse` (streaming tokens) → `onCompleteResponse`. This means tool results arrive before the agent's text tokens.

Currently, `ToolCompleteMessage` immediately adds to `chatHistory`, causing tool boxes to appear above the agent's explanatory text. Additionally, `ChatView` passes entire multi-line strings to `Style.render()`, which wraps the full string in a single ANSI escape sequence — breaking indent and styling across `\n` boundaries.

## Goals / Non-Goals

**Goals:**
- Natural message ordering: `[You] → [Agent text] → [Tool result box]`
- Consistent left-indent and ANSI styling on all lines of multi-line chat entries
- Tool results remain visible during streaming (shown in transient area before being committed)

**Non-Goals:**
- Modifying `StreamBridge` or LangChain4j callback ordering
- Markdown rendering or rich text parsing
- Changing `ToolResultView` box layout

## Decisions

### Decision 1: Buffer tool results in AgentModel

**Choice**: Add a `pendingToolResults` list to `AgentModel`; `ToolCompleteMessage` appends to the buffer instead of `chatHistory`. On `StreamCompleteMessage`, the agent text is committed first, then buffered tools are flushed.

**Alternative considered**: Reorder `chatHistory` entries after the fact (sort by timestamp). Rejected because it adds complexity and the insertion order is the contract — timestamps could be equal for near-simultaneous events.

**Alternative considered**: Modify `StreamBridge` to delay `ToolCompleteMessage` until after `StreamCompleteMessage`. Rejected because it changes the message protocol and hides tool results from the user during streaming.

### Decision 2: Show pending tools in transient streaming area

**Choice**: `refreshViewport()` renders `pendingToolResults` in the transient area (below committed history, above the streaming cursor). This way users see tool results in real-time even though they're not yet committed.

**Rationale**: The user shouldn't have to wait for the full stream to complete before seeing what the tool returned. The transient area already shows the spinner and partial response.

### Decision 3: Line-by-line rendering with split + per-line Style.render()

**Choice**: `ChatView` splits content on `\n` (with `-1` limit to preserve trailing empties) and calls `Style.render()` on each line individually, prepending `"  "` indent to each.

**Alternative considered**: Use lipgloss `Style.width()` or padding to handle multi-line layout. Rejected because lipgloss styles are designed for single-line content — setting width would add unwanted wrapping rather than preserving the original line structure.

### Decision 4: Badge on its own line

**Choice**: Badge labels (e.g., `Agent`, `You`) render on their own line above the content, rather than inline with the first line.

**Rationale**: When content is multi-line, having the badge inline with the first line but not subsequent lines creates visual inconsistency. Putting the badge on its own line gives a cleaner layout for both single-line and multi-line messages.

## Risks / Trade-offs

- **[Tool-only responses]** If the agent calls a tool but produces no text (empty `StreamCompleteMessage`), tools are still flushed correctly — the empty-text guard `!response.isBlank()` skips the agent entry but `addAll(pendingToolResults)` still runs. → No mitigation needed.

- **[Error during streaming]** If an error occurs mid-stream with pending tool results, they could be lost. → Mitigation: `ErrorMessage` handler also flushes `pendingToolResults` before adding the error entry.

- **[Visual change]** Badge moving to its own line is a minor visual change users will notice. → Acceptable trade-off for consistent multi-line rendering.
