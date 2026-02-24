## Why

A polished TUI needs consistent colors, borders, and layout. lipgloss (ported in tui4j) provides styling primitives. This change adds a theme, color-coded chat roles, a header bar, and composed layout.

## What Changes

- Create `Theme.java` with lipgloss styles for each role (user=cyan, agent=green, tool=yellow, error=red)
- Create `HeaderView.java` showing app name, current model, and tool count
- Create `ChatView.java` rendering chat entries with role-specific colors
- Compose the full layout: header bar → chat viewport → input area

## Non-goals

- No theme switching or customization
- No true markdown rendering — just ANSI color and borders

## Capabilities

### New Capabilities
- `theme`: lipgloss style definitions for colors, borders, and role-specific formatting
- `layout-composition`: Composed view with header bar, chat viewport, and input area

### Modified Capabilities
