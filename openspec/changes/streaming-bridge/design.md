## Context

LangChain4j's `TokenStream` delivers tokens via `onNext(String token)` on a background thread. tui4j's event loop is single-threaded — you must use `Program.send(Message)` to inject events from other threads.

## Goals / Non-Goals

**Goals:**
- User submits message → AI streams response token-by-token into viewport
- Thread-safe bridging between LangChain4j callbacks and tui4j event loop
- Anthropic prompt caching enabled for system messages and tools

**Non-Goals:**
- No tool execution handling yet
- No chat memory persistence beyond in-memory window

## Decisions

**StreamBridge holds Program reference**: The bridge receives a `Program` instance and calls `program.send(new StreamTokenMessage(token))` from the TokenStream callback.

**MessageWindowChatMemory with 50 messages**: Keeps recent context without unbounded growth. Configurable later.

**AnthropicStreamingChatModel with cacheSystemMessages + cacheTools**: Reduces latency and cost for repeated calls in the same TUI session.

**StringBuilder for current response**: AgentModel accumulates tokens in a `StringBuilder`, then on `StreamCompleteMessage`, creates the final ChatEntry.

## Risks / Trade-offs

- [Token flood could overwhelm TUI rendering] → Buffer tokens into word-sized chunks if needed
- [Program.send() thread safety] → tui4j documents this as the correct cross-thread approach
