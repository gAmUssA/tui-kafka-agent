## Why

The app needs a terminal UI foundation. tui4j implements the Elm Architecture (Model/Update/View) which gives us a predictable, testable UI loop. This change creates the minimal tui4j Program that takes over the terminal and handles keyboard input.

## What Changes

- Create `AgentModel` implementing tui4j's `Model` interface with `init()`, `update()`, `view()`
- Wire `AgentModel` into a tui4j `Program` that enters alternate screen mode
- Handle basic key events: `Ctrl+C` to quit, text input echoed to screen

## Non-goals

- No chat history or AI integration — just the raw TUI loop
- No styling or layout composition

## Capabilities

### New Capabilities
- `tui-program`: tui4j Program lifecycle — init, run, shutdown with alternate screen buffer
- `agent-model`: Core AgentModel implementing Model interface with basic key event handling

### Modified Capabilities
