## Why

The AI needs to stream responses token-by-token into the TUI. LangChain4j's `TokenStream` delivers tokens on a callback thread, but tui4j's event loop requires messages posted via `Program.send()`. A bridge is needed to connect these two async worlds.

## What Changes

- Configure `AnthropicStreamingChatModel` with prompt caching enabled
- Create `AgentService` interface using LangChain4j `AiServices` builder
- Create `StreamBridge` that converts `TokenStream` callbacks into tui4j custom Messages
- Define custom Message types: `StreamTokenMessage`, `StreamCompleteMessage`, `ErrorMessage`
- Handle these messages in AgentModel to append tokens to the current response

## Non-goals

- No tool calling yet — pure text streaming only
- No extended thinking display
- No model switching at runtime

## Capabilities

### New Capabilities
- `anthropic-model`: Anthropic Claude streaming model configuration with prompt caching
- `stream-bridge`: Bridge between LangChain4j TokenStream and tui4j message loop
- `custom-messages`: Custom Message types for async AI events

### Modified Capabilities
