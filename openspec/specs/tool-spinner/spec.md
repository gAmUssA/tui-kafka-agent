## ADDED Requirements

### Requirement: Spinner during tool execution
AgentModel SHALL show a tui4j `SpinnerModel` animation when a tool is being executed.

#### Scenario: Tool starts executing
- **WHEN** a `ToolExecutingMessage("listTopics")` is received
- **THEN** the spinner starts with text "Calling listTopics()..."

#### Scenario: Tool completes
- **WHEN** a `ToolCompleteMessage` is received
- **THEN** the spinner stops

### Requirement: ToolExecutingMessage
A `ToolExecutingMessage(String toolName)` record SHALL be sent when a tool call begins.

#### Scenario: Message carries tool name
- **WHEN** the AI decides to call a tool
- **THEN** `ToolExecutingMessage` is sent with the tool method name
