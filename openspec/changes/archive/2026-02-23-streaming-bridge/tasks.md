## 1. Custom Messages

- [x] 1.1 Create `StreamTokenMessage.java` record implementing Message
- [x] 1.2 Create `StreamCompleteMessage.java` record implementing Message
- [x] 1.3 Create `ErrorMessage.java` record implementing Message

## 2. Anthropic Model Setup

- [x] 2.1 Create `AnthropicConfig.java` that builds `AnthropicStreamingChatModel` from AppConfig
- [x] 2.2 Create `AgentAssistant` interface with `TokenStream chat(String sessionId, String message)`
- [x] 2.3 Wire AiServices builder with streaming model and MessageWindowChatMemory(50)

## 3. Stream Bridge

- [x] 3.1 Create `StreamBridge.java` that takes a `Program` reference
- [x] 3.2 Implement `sendMessage(String userInput)` that calls assistant.chat() and wires TokenStream callbacks to Program.send()
- [x] 3.3 Handle onNext → StreamTokenMessage, onComplete → StreamCompleteMessage, onError → ErrorMessage

## 4. Model Integration

- [x] 4.1 Add `isThinking` boolean and `currentResponse` StringBuilder to AgentModel
- [x] 4.2 Handle StreamTokenMessage in update() — append token, refresh viewport
- [x] 4.3 Handle StreamCompleteMessage in update() — create ChatEntry, reset state
- [x] 4.4 Handle ErrorMessage in update() — show error in viewport
- [x] 4.5 On Enter submit, call StreamBridge.sendMessage() instead of local echo
- [x] 4.6 Verify: type a question, see streaming response token-by-token
