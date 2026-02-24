## ADDED Requirements

### Requirement: Header bar
A `HeaderView` SHALL render a single-line header showing app name, current model name, and tool count.

#### Scenario: Header displays state
- **WHEN** the app is running with claude-sonnet and 5 tools
- **THEN** the header shows "kafka-agent | claude-sonnet | 5 tools"

### Requirement: Three-section layout
`AgentModel.view()` SHALL compose three sections vertically: header bar (top), chat viewport (middle, fills remaining space), input area (bottom).

#### Scenario: Full layout rendered
- **WHEN** view() is called
- **THEN** the returned string contains header, viewport content, and input area in that order
