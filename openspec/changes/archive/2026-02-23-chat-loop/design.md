## Context

tui4j provides `TextAreaModel` (multi-line input) and `ViewportModel` (scrollable content). We compose these inside AgentModel — textarea at bottom, viewport above.

## Goals / Non-Goals

**Goals:**
- User types in textarea, presses Enter, message appears in viewport
- Viewport auto-scrolls to show latest messages
- Chat history maintained as `List<ChatEntry>`

**Non-Goals:**
- No AI integration — just local echo
- No styled rendering — plain text for now

## Decisions

**TextArea over TextInput**: TextArea supports multi-line which is better for longer prompts. Single-line Enter submits; Shift+Enter for newlines.

**ChatEntry as Java record**: `record ChatEntry(Role role, String content, Instant timestamp)` — immutable, clean.

**Viewport content rebuilt from history**: On each `view()`, render all ChatEntry objects into a string and set as viewport content. Simple and correct; optimize later if needed.

## Risks / Trade-offs

- [Rebuilding viewport content on every view] → Fine for hundreds of messages; revisit if performance degrades
