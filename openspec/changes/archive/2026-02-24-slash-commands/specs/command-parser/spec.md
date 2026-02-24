## ADDED Requirements

### Requirement: Parse slash commands
`CommandParser` SHALL detect input starting with `/` and parse it into a `SlashCommand(name, args)` record.

#### Scenario: Valid command with args
- **WHEN** input is "/model sonnet"
- **THEN** `SlashCommand("model", ["sonnet"])` is returned

#### Scenario: Command without args
- **WHEN** input is "/clear"
- **THEN** `SlashCommand("clear", [])` is returned

#### Scenario: Not a command
- **WHEN** input is "what topics do I have?"
- **THEN** `Optional.empty()` is returned

### Requirement: Route to SlashCommandMsg
When a slash command is parsed, a `SlashCommandMsg` SHALL be sent instead of a chat message.

#### Scenario: Command routed
- **WHEN** user types "/help" and presses Enter
- **THEN** a `SlashCommandMsg` is dispatched (not sent to AI)
