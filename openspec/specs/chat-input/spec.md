## ADDED Requirements

### Requirement: TextArea input field
AgentModel SHALL include a tui4j `TextAreaModel` rendered at the bottom of the screen for user input.

#### Scenario: User types text
- **WHEN** the user types characters
- **THEN** the text appears in the input area

### Requirement: Enter to submit
Pressing Enter SHALL submit the current input text, add it to chat history as a user message, and clear the input field.

#### Scenario: Submit message
- **WHEN** the user types "hello" and presses Enter
- **THEN** a ChatEntry with role USER and content "hello" is added to chat history and the input field is cleared

### Requirement: Empty submit ignored
Pressing Enter with an empty input SHALL do nothing.

#### Scenario: Empty submit
- **WHEN** the input field is empty and the user presses Enter
- **THEN** no ChatEntry is added to chat history
