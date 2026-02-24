## ADDED Requirements

### Requirement: Per-line ANSI styling for chat entries
`ChatView` SHALL split multi-line content on `\n` and apply `Style.render()` to each line individually, so ANSI escape codes do not span newline boundaries.

#### Scenario: Agent response with multiple lines
- **WHEN** an AGENT chat entry contains `"Line 1\nLine 2\nLine 3"`
- **THEN** each line is rendered with its own `Style.render()` call
- **AND** ANSI styling is applied independently to each line

### Requirement: Consistent left indent on all lines
Every line of a multi-line chat entry SHALL be prefixed with `"  "` (two spaces) for visual alignment under the badge label.

#### Scenario: Multi-line user message
- **WHEN** a USER chat entry contains multiple lines
- **THEN** each line has a `"  "` prefix before the styled content

#### Scenario: Empty lines preserved
- **WHEN** content contains `"\n\n"` (double newline)
- **THEN** the empty line between content lines is preserved in output

### Requirement: Badge label on its own line
For USER, AGENT, ERROR, and non-boxed TOOL entries, the badge label (e.g., "Agent", "You") SHALL render on its own line above the content.

#### Scenario: Single-line agent message
- **WHEN** an AGENT entry with single-line content is rendered
- **THEN** output is: badge line, then content line (two separate lines)

#### Scenario: Multi-line error message
- **WHEN** an ERROR entry with multi-line content is rendered
- **THEN** output is: badge line, then each content line on its own line

### Requirement: Streaming partial response uses per-line rendering
The streaming partial response in `refreshViewport()` SHALL use the same line-by-line rendering pattern, with the cursor block appended to the last line.

#### Scenario: Streaming multi-line response
- **WHEN** the agent is streaming a response containing newlines
- **THEN** each line is individually styled with `"  "` indent
- **AND** the cursor block character appears at the end of the last line only
