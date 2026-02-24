## ADDED Requirements

### Requirement: Role-specific colors
`Theme` SHALL define lipgloss styles with colors: USER=cyan, AGENT=green, TOOL=yellow, ERROR=red.

#### Scenario: User message styled
- **WHEN** a ChatEntry with role USER is rendered
- **THEN** the "You:" prefix is displayed in cyan

### Requirement: Bordered tool results
Tool results SHALL be rendered with a lipgloss border and a header showing the tool name.

#### Scenario: Tool result box
- **WHEN** a TOOL ChatEntry is rendered
- **THEN** it appears in a bordered box with yellow border color
