## MODIFIED Requirements

### Requirement: Scrollable chat viewport
AgentModel SHALL include a tui4j `ViewportModel` displaying the chat history above the input area.

#### Scenario: Messages displayed
- **WHEN** chat history contains messages
- **THEN** the viewport shows each message with a badge label on its own line and content lines indented below

### Requirement: ChatEntry record
A `ChatEntry` record SHALL store `role` (enum: USER, AGENT, TOOL, ERROR, PRERENDERED), `content` (String), `toolName` (String, nullable), and `timestamp` (Instant).

#### Scenario: Create chat entry
- **WHEN** a user submits a message
- **THEN** a ChatEntry is created with role USER, the message content, and current timestamp
