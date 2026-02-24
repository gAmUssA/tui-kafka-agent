## 1. Parser

- [x] 1.1 Create `SlashCommand.java` record with name and args list
- [x] 1.2 Create `CommandParser.java` with `Optional<SlashCommand> parse(String input)`
- [x] 1.3 Create `SlashCommandMsg.java` message record

## 2. Command Routing

- [x] 2.1 In AgentModel, intercept Enter submit: if CommandParser returns a command, handle it instead of chat
- [x] 2.2 Handle slash commands in update() with switch on command name

## 3. Command Implementations

- [x] 3.1 Implement `/help` — list all commands with descriptions
- [x] 3.2 Implement `/clear` — clear chat history and memory
- [x] 3.3 Implement `/tools` — list registered tool names
- [x] 3.4 Implement `/model` — create `ModelSwitcher.java`, rebuild AI service with new model
- [x] 3.5 Implement `/thinking` — toggle extended thinking on AnthropicStreamingChatModel
- [x] 3.6 Implement `/topics` — send to AI which uses MCP tools to list topics
- [x] 3.7 Implement `/sql` — send to AI which uses MCP tools to execute Flink SQL
- [x] 3.8 Handle unknown commands — show error with /help suggestion
