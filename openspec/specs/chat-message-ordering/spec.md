## ADDED Requirements

### Requirement: Tool results appear after agent text in chat history
When a streaming response completes, the agent's text response SHALL be committed to `chatHistory` before any tool results from that interaction.

#### Scenario: Agent calls tool and produces text
- **WHEN** the agent calls a tool (e.g., `list-topics`) and then produces explanatory text
- **THEN** the chat history order is: `[User message] → [Agent text] → [Tool result box]`

#### Scenario: Agent calls multiple tools
- **WHEN** the agent calls two tools during a single interaction and produces text
- **THEN** agent text appears first, followed by both tool results in the order they completed

### Requirement: Tool results buffered during streaming
`AgentModel` SHALL buffer tool results in a `pendingToolResults` list during streaming instead of committing them to `chatHistory` immediately.

#### Scenario: Tool completes while streaming
- **WHEN** a `ToolCompleteMessage` is received while `isStreaming` is true
- **THEN** the tool result is added to `pendingToolResults` (not `chatHistory`)
- **AND** `isToolExecuting` is set to false

### Requirement: Buffered tools flushed on stream complete
On `StreamCompleteMessage`, `AgentModel` SHALL commit the agent response text first, then flush all `pendingToolResults` to `chatHistory`, then clear the buffer.

#### Scenario: Stream completes with buffered tools
- **WHEN** `StreamCompleteMessage` is received with pending tool results
- **THEN** agent text is added to `chatHistory`
- **AND** all pending tool results are appended to `chatHistory` after the agent text
- **AND** `pendingToolResults` is cleared

### Requirement: Buffered tools flushed on error
On `ErrorMessage`, `AgentModel` SHALL flush any `pendingToolResults` to `chatHistory` before adding the error entry, so tool results are not lost.

#### Scenario: Error with pending tools
- **WHEN** an `ErrorMessage` is received while `pendingToolResults` is non-empty
- **THEN** pending tool results are flushed to `chatHistory`
- **AND** the error entry is added after them

### Requirement: Pending tools visible during streaming
Pending tool results SHALL be rendered in the transient streaming area of `refreshViewport()` so users can see them in real-time before they are committed to history.

#### Scenario: Tool result shown while agent is still streaming
- **WHEN** a tool completes and the agent is still streaming text
- **THEN** the tool result box is visible in the viewport below committed history
- **AND** it appears above the streaming cursor
