## Why

Users need to type messages and see responses in a scrollable chat history. This is the core interaction loop — TextArea for input, Viewport for chat history, Enter to submit.

## What Changes

- Add tui4j `TextAreaModel` for multi-line input at the bottom of the screen
- Add tui4j `ViewportModel` for scrollable chat history
- Create `ChatEntry` record to represent user/agent messages
- Handle Enter key to submit input, append to chat history, clear input
- Auto-scroll viewport to bottom on new messages

## Non-goals

- No AI responses yet — submitted messages just echo back as "You: <message>"
- No markdown rendering
- No styling

## Capabilities

### New Capabilities
- `chat-input`: TextArea-based message input with Enter-to-submit
- `chat-viewport`: Scrollable viewport displaying chat history with ChatEntry records

### Modified Capabilities
