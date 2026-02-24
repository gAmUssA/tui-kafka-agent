## Context

tui4j is a Java port of Go's Bubble Tea. The core loop: Program creates a Model, calls `init()` for startup commands, then loops `update(msg)` → `view()` on every event. The view returns a String that IS the entire screen.

## Goals / Non-Goals

**Goals:**
- tui4j Program running in alternate screen buffer
- AgentModel handling KeyMsg for quit (Ctrl+C) and passing through to sub-components later

**Non-Goals:**
- No sub-components (viewport, textarea) yet — those come in chat-loop

## Decisions

**Single AgentModel as root**: One top-level Model that will later compose sub-models. Keeps the entry point simple.

**Alternate screen buffer**: Use `Program.withAltScreen()` so the app doesn't pollute terminal history.

**Quit on Ctrl+C and Esc**: Standard TUI convention. Return `Cmd.quit()` from update.

## Risks / Trade-offs

- [tui4j API may differ from Bubble Tea docs] → Reference Brief source code as working example
