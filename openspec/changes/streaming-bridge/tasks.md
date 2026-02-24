## 1. Custom Messages

- [ ] 1.1 Create `StreamTokenMessage.java` record implementing Message
- [ ] 1.2 Create `StreamCompleteMessage.java` record implementing Message
- [ ] 1.3 Create `ErrorMessage.java` record implementing Message

## 2. Anthropic Model Setup

- [ ] 2.1 Create `AnthropicConfig.java` that builds `AnthropicStreamingChatModel` from AppConfig
- [ ] 2.2 Create `AgentAssistant` interface with `TokenStream chat(String sessionId, String message)`
- [ ] 2.3 Wire AiServices builder with streaming model and MessageWindowChatMemory(50)

## 3. Stream Bridge

- [ ] 3.1 Create `StreamBridge.java` that takes a `Program` reference
- [ ] 3.2 Implement `sendMessage(String userInput)` that calls assistant.chat() and wires TokenStream callbacks to Program.send()
- [ ] 3.3 Handle onNext → StreamTokenMessage, onComplete → StreamCompleteMessage, onError → ErrorMessage

## 4. Model Integration

- [ ] 4.1 Add `isThinking` boolean and `currentResponse` StringBuilder to AgentModel
- [ ] 4.2 Handle StreamTokenMessage in update() — append token, refresh viewport
- [ ] 4.3 Handle StreamCompleteMessage in update() — create ChatEntry, reset state
- [ ] 4.4 Handle ErrorMessage in update() — show error in viewport
- [ ] 4.5 On Enter submit, call StreamBridge.sendMessage() instead of local echo
- [ ] 4.6 Verify: type a question, see streaming response token-by-token
