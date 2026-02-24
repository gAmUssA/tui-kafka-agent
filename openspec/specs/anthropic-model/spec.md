## ADDED Requirements

### Requirement: Anthropic streaming model configured
The app SHALL create an `AnthropicStreamingChatModel` with API key from config, model name from config, max tokens 4096, and prompt caching enabled.

#### Scenario: Model created with caching
- **WHEN** the streaming model is initialized
- **THEN** `cacheSystemMessages` and `cacheTools` are both enabled

### Requirement: AI Service interface
An `AgentAssistant` interface SHALL be created using LangChain4j `AiServices.builder()` with the streaming model and `MessageWindowChatMemory` (max 50 messages).

#### Scenario: Chat returns token stream
- **WHEN** `assistant.chat(sessionId, message)` is called
- **THEN** a `TokenStream` is returned that delivers tokens asynchronously
