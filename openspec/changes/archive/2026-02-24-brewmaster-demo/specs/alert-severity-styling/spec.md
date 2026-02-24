## ADDED Requirements

### Requirement: Severity keyword coloring in tool results
`ToolResultView` SHALL apply color styling to severity keywords found in tool result text: `CRITICAL` in red, `WARNING` in yellow/gold, `INFO` in blue.

#### Scenario: CRITICAL keyword
- **WHEN** a tool result contains the text "CRITICAL"
- **THEN** occurrences of "CRITICAL" are rendered in red (Theme.ERROR color)

#### Scenario: WARNING keyword
- **WHEN** a tool result contains the text "WARNING"
- **THEN** occurrences of "WARNING" are rendered in gold (Theme.TOOL color)

#### Scenario: No severity keywords
- **WHEN** a tool result contains no severity keywords
- **THEN** the text is rendered with default tool result styling (no change)

#### Scenario: Multiple severity levels in one result
- **WHEN** a tool result contains both "CRITICAL" and "WARNING"
- **THEN** each keyword is colored according to its severity independently
