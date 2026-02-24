## Why

Power users need quick access to common operations without typing natural language. Slash commands (like `/model sonnet`, `/clear`, `/topics`) provide direct control over the agent and shortcuts for frequent Kafka/Flink operations.

## What Changes

- Create slash command parser that detects input starting with `/`
- Implement commands: `/model`, `/thinking`, `/clear`, `/tools`, `/history`, `/export`, `/topics`, `/sql`, `/help`
- Show command completions/help when user types `/`

## Non-goals

- No custom user-defined commands
- No command aliases
- `/mcp` command is handled in a separate mcp-integration change

## Capabilities

### New Capabilities
- `command-parser`: Parse slash commands from input and route to handlers
- `builtin-commands`: Implementation of all built-in slash commands

### Modified Capabilities
