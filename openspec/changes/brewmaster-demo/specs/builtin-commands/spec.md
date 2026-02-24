## ADDED Requirements

### Requirement: /reset-brewery command registration
The command parser SHALL recognize `/reset-brewery` as a valid slash command.

#### Scenario: Parse reset-brewery
- **WHEN** user input is `/reset-brewery`
- **THEN** `CommandParser.parse()` returns a `SlashCommand` with name `"reset-brewery"` and empty args

### Requirement: /help includes reset-brewery
The `/help` output SHALL include `/reset-brewery` with description "Reset demo (delete brewery topics & Flink jobs)".

#### Scenario: Help lists reset-brewery
- **WHEN** user types `/help`
- **THEN** the help text includes a line for `/reset-brewery`
