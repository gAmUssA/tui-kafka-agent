## Context

tui4j includes a lipgloss port for terminal styling. Styles define colors, padding, borders, and width. The view() method composes styled strings vertically.

## Goals / Non-Goals

**Goals:**
- Consistent color palette across all UI elements
- Header bar showing connection state
- Role-specific chat message styling

**Non-Goals:**
- No 256-color or truecolor detection — use basic ANSI colors

## Decisions

**Theme as static style constants**: `Theme.java` with public static final Style fields. No runtime theme switching needed.

**Color per role**: USER=cyan, AGENT=green, TOOL=yellow, ERROR=red, SYSTEM=dimmed gray. High contrast on dark terminals.

**Header bar with model info**: `kafka-agent | claude-sonnet | 5 tools` — updates when model switches or tools are added.

**Vertical composition in view()**: Header + viewport + input, joined with newlines. lipgloss `JoinVertical` if available, otherwise string concatenation.

## Risks / Trade-offs

- [lipgloss port may not have all Go features] → Fall back to raw ANSI escape codes if needed
