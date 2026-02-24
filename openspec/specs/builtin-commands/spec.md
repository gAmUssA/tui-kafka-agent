### Requirement: /model command
`/model <name>` SHALL switch the active Claude model. Accepted names: `sonnet`, `haiku`, `opus`.

#### Scenario: Switch to haiku
- **WHEN** user types "/model haiku"
- **THEN** the AI service is rebuilt with claude-3-5-haiku model and a confirmation message appears

### Requirement: /thinking command
`/thinking` SHALL toggle extended thinking mode on or off.

#### Scenario: Enable thinking
- **WHEN** user types "/thinking" and thinking is off
- **THEN** thinking mode is enabled and a confirmation message appears

### Requirement: /clear command
`/clear` SHALL clear chat history and reset chat memory.

#### Scenario: Clear chat
- **WHEN** user types "/clear"
- **THEN** chat history is emptied and the viewport is cleared

### Requirement: /tools command
`/tools` SHALL list all available tools with their descriptions.

#### Scenario: List tools
- **WHEN** user types "/tools"
- **THEN** each tool name and description is shown in the viewport

### Requirement: /help command
`/help` SHALL display all available slash commands with brief descriptions.

#### Scenario: Show help
- **WHEN** user types "/help"
- **THEN** a list of all commands and their usage is displayed

### Requirement: /topics shortcut
`/topics` SHALL directly invoke the listTopics tool without going through the AI.

#### Scenario: Quick list topics
- **WHEN** user types "/topics"
- **THEN** the listTopics tool is called and results displayed

### Requirement: /sql shortcut
`/sql <query>` SHALL directly invoke the submitFlinkSql tool with the given query.

#### Scenario: Quick SQL
- **WHEN** user types "/sql SELECT * FROM orders LIMIT 5"
- **THEN** the submitFlinkSql tool is called with that query and results displayed

### Requirement: Unknown command
Unknown commands SHALL show a help message suggesting `/help`.

#### Scenario: Invalid command
- **WHEN** user types "/foo"
- **THEN** a message "Unknown command: /foo. Type /help for available commands." is shown

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
