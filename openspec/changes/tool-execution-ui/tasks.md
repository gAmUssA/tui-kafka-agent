## 1. Custom Messages

- [ ] 1.1 Create `ToolExecutingMessage.java` record with toolName field
- [ ] 1.2 Create `ToolCompleteMessage.java` record with toolName and result fields

## 2. Spinner Integration

- [ ] 2.1 Add `SpinnerModel` to AgentModel
- [ ] 2.2 Handle ToolExecutingMessage: start spinner with "Calling <toolName>()..." text
- [ ] 2.3 Handle ToolCompleteMessage: stop spinner

## 3. Tool Result Rendering

- [ ] 3.1 Create `ToolResultView.java` that renders a tool result as a bordered box with tool name header
- [ ] 3.2 Handle ToolCompleteMessage: create ChatEntry with TOOL role
- [ ] 3.3 Render TOOL role entries using ToolResultView in the viewport

## 4. StreamBridge Integration

- [ ] 4.1 Wire tool execution callbacks in StreamBridge to send ToolExecutingMessage and ToolCompleteMessage
- [ ] 4.2 Verify: ask a question that triggers a tool → spinner shows → bordered result appears
