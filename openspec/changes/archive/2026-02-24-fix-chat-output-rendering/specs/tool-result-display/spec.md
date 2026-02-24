## MODIFIED Requirements

### Requirement: Tool results rendered as bordered box
Tool results SHALL be rendered as a bordered box in the chat viewport, visually distinct from regular chat messages. Tool results SHALL be committed to `chatHistory` after the agent's text response, not immediately on `ToolCompleteMessage`.

#### Scenario: Tool result displayed
- **WHEN** a `ToolCompleteMessage("listTopics", result)` is received during streaming
- **THEN** the tool result is buffered in `pendingToolResults`
- **AND** on `StreamCompleteMessage`, it is committed to `chatHistory` after the agent text
- **AND** it is rendered with a border and tool name header

### Requirement: ToolCompleteMessage
A `ToolCompleteMessage(String toolName, String result)` record SHALL be sent when a tool finishes.

#### Scenario: Message carries result
- **WHEN** a tool execution completes
- **THEN** `ToolCompleteMessage` is sent with tool name and result string
