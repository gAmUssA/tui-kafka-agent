## ADDED Requirements

### Requirement: Scrollable chat viewport
AgentModel SHALL include a tui4j `ViewportModel` displaying the chat history above the input area.

#### Scenario: Messages displayed
- **WHEN** chat history contains messages
- **THEN** the viewport shows each message prefixed with its role ("You:" or "Agent:")

### Requirement: Auto-scroll on new message
The viewport SHALL auto-scroll to the bottom when a new ChatEntry is added.

#### Scenario: New message scrolls down
- **WHEN** a new message is added and the viewport was at the bottom
- **THEN** the viewport scrolls to show the new message

### Requirement: ChatEntry record
A `ChatEntry` record SHALL store `role` (enum: USER, AGENT, TOOL, ERROR), `content` (String), and `timestamp` (Instant).

#### Scenario: Create chat entry
- **WHEN** a user submits a message
- **THEN** a ChatEntry is created with role USER, the message content, and current timestamp
