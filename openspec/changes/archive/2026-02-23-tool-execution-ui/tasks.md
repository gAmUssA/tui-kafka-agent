## 1. Custom Messages

- [x] 1.1 Create `ToolExecutingMessage.java` record with toolName field
- [x] 1.2 Create `ToolCompleteMessage.java` record with toolName and result fields

## 2. Spinner Integration

- [x] 2.1 Add `SpinnerModel` to AgentModel
- [x] 2.2 Handle ToolExecutingMessage: start spinner with "Calling <toolName>()..." text
- [x] 2.3 Handle ToolCompleteMessage: stop spinner

## 3. Tool Result Rendering

- [x] 3.1 Create `ToolResultView.java` that renders a tool result as a bordered box with tool name header
- [x] 3.2 Handle ToolCompleteMessage: create ChatEntry with TOOL role
- [x] 3.3 Render TOOL role entries using ToolResultView in the viewport

## 4. StreamBridge Integration

- [x] 4.1 Wire tool execution callbacks in StreamBridge to send ToolExecutingMessage and ToolCompleteMessage
- [x] 4.2 Verify: ask a question that triggers a tool → spinner shows → bordered result appears
