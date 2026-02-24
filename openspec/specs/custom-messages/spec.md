## ADDED Requirements

### Requirement: Custom Message types
The app SHALL define these Message records: `StreamTokenMessage(String token)`, `StreamCompleteMessage(String fullResponse)`, `ErrorMessage(String error)`.

#### Scenario: Messages implement tui4j Message interface
- **WHEN** any custom message is created
- **THEN** it implements the tui4j `Message` interface

### Requirement: AgentModel handles StreamTokenMessage
AgentModel SHALL append the token to a `StringBuilder` and update the viewport when receiving `StreamTokenMessage`.

#### Scenario: Token appended to response
- **WHEN** a `StreamTokenMessage` is received
- **THEN** the current response StringBuilder is appended and the viewport shows the partial response

### Requirement: AgentModel handles StreamCompleteMessage
AgentModel SHALL create a final `ChatEntry` with role AGENT when receiving `StreamCompleteMessage`, clear the StringBuilder, and set `isThinking` to false.

#### Scenario: Response finalized
- **WHEN** a `StreamCompleteMessage` is received
- **THEN** a ChatEntry is created, spinner stops, and input is re-enabled
