## Context

Brief (the reference tui4j app) implements slash commands by checking if input starts with `/` before sending to the AI. We follow the same pattern.

## Goals / Non-Goals

**Goals:**
- Parse `/command arg1 arg2` syntax
- Route to handler methods
- Show help text for unknown commands

**Non-Goals:**
- No tab completion for commands
- No command history

## Decisions

**CommandParser as separate class**: Takes input string, returns `Optional<SlashCommand>`. If input doesn't start with `/`, returns empty. Keeps parsing logic out of AgentModel.

**SlashCommand record**: `record SlashCommand(String name, List<String> args)` — simple, testable.

**Commands handled in AgentModel.update()**: When a `SlashCommandMsg` is received, switch on command name. Keeps all state mutations in one place.

**/model triggers ModelSwitcher**: Create `ModelSwitcher` class that rebuilds the AgentAssistant with a new model name. Supports `sonnet`, `haiku`, `opus` as shortcuts.

## Risks / Trade-offs

- [Model switching rebuilds the AI service] → Acceptable; happens rarely and chat memory is preserved
