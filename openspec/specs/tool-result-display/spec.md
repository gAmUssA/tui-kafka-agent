## ADDED Requirements

### Requirement: Tool results rendered as bordered box
Tool results SHALL be rendered as a bordered box in the chat viewport, visually distinct from regular chat messages.

#### Scenario: Tool result displayed
- **WHEN** a `ToolCompleteMessage("listTopics", result)` is received
- **THEN** a ChatEntry with role TOOL is created and rendered with a border and tool name header

### Requirement: ToolCompleteMessage
A `ToolCompleteMessage(String toolName, String result)` record SHALL be sent when a tool finishes.

#### Scenario: Message carries result
- **WHEN** a tool execution completes
- **THEN** `ToolCompleteMessage` is sent with tool name and result string
