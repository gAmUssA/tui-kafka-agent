## 1. Parser

- [ ] 1.1 Create `SlashCommand.java` record with name and args list
- [ ] 1.2 Create `CommandParser.java` with `Optional<SlashCommand> parse(String input)`
- [ ] 1.3 Create `SlashCommandMsg.java` message record

## 2. Command Routing

- [ ] 2.1 In AgentModel, intercept Enter submit: if CommandParser returns a command, send SlashCommandMsg instead of chat
- [ ] 2.2 Handle SlashCommandMsg in update() with switch on command name

## 3. Command Implementations

- [ ] 3.1 Implement `/help` — list all commands with descriptions
- [ ] 3.2 Implement `/clear` — clear chat history and memory
- [ ] 3.3 Implement `/tools` — list registered tool names and descriptions
- [ ] 3.4 Implement `/model` — create `ModelSwitcher.java`, rebuild AI service with new model
- [ ] 3.5 Implement `/thinking` — toggle extended thinking on AnthropicStreamingChatModel
- [ ] 3.6 Implement `/topics` — call listTopics directly, display result
- [ ] 3.7 Implement `/sql` — call submitFlinkSql with args joined as query
- [ ] 3.8 Handle unknown commands — show error with /help suggestion
