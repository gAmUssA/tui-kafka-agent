## 1. Data Model

- [x] 1.1 Create `ChatEntry.java` record with Role enum (USER, AGENT, TOOL, ERROR), content, timestamp
- [x] 1.2 Add `List<ChatEntry> chatHistory` field to AgentModel

## 2. Input Component

- [x] 2.1 Add TextInput to AgentModel, initialize in `init()` (Note: tui4j 0.2.5 has TextInput, not TextArea)
- [x] 2.2 Delegate key events to TextInput in `update()`
- [x] 2.3 Handle Enter key: extract text, create ChatEntry, clear input, ignore if empty

## 3. Viewport Component

- [x] 3.1 Implement manual scrollable viewport (tui4j 0.2.5 has no Viewport class) with scroll offset
- [x] 3.2 Render chat history into viewport content string in `view()`
- [x] 3.3 Auto-scroll to bottom after adding new messages, pgup/pgdn for manual scroll

## 4. Layout

- [x] 4.1 Compose `view()` to show header + chat area + separator + input
- [ ] 4.2 Verify: type message, press Enter, see it in viewport above
