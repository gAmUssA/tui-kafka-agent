package com.example.agent.agent;

import dev.langchain4j.service.TokenStream;

/**
 * Drives one conversational turn through the assistant and publishes the
 * result as {@link AgentEvent}s. Knows nothing about the UI.
 * <p>
 * Today the assistant is a single streaming {@code AiServices} interface;
 * the same sink contract will carry {@code AgentListener} callbacks from the
 * multi-agent system without the UI changing.
 */
public class StreamBridge {

    private static final String ASSISTANT_NAME = "assistant";

    private final AgentEvent.Sink sink;
    private volatile AgentAssistant assistant;
    private final String sessionId = "default";

    public StreamBridge(AgentEvent.Sink sink, AgentAssistant assistant) {
        this.sink = sink;
        this.assistant = assistant;
    }

    /**
     * Replace the assistant (e.g., after rebuilding with MCP tools or a new model).
     */
    public void setAssistant(AgentAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * Start a streaming chat. Tokens arrive as {@link AgentEvent.Token}, tool
     * calls as {@link AgentEvent.ToolStarted}/{@link AgentEvent.ToolFinished},
     * completion as {@link AgentEvent.Completed}, failure as {@link AgentEvent.Failed}.
     */
    public void sendMessage(String userInput) {
        long started = System.currentTimeMillis();
        sink.accept(new AgentEvent.AgentStarted(ASSISTANT_NAME));

        TokenStream tokenStream;
        try {
            tokenStream = assistant.chat(sessionId, userInput);
        } catch (RuntimeException e) {
            sink.accept(new AgentEvent.Failed(ASSISTANT_NAME, e));
            return;
        }

        StringBuilder fullResponse = new StringBuilder();

        tokenStream
                .beforeToolExecution(before -> sink.accept(new AgentEvent.ToolStarted(
                        ASSISTANT_NAME, before.request().name(), before.request().arguments())))
                .onToolExecuted(execution -> sink.accept(new AgentEvent.ToolFinished(
                        ASSISTANT_NAME, execution.request().name(), execution.result())))
                .onPartialResponse(token -> {
                    fullResponse.append(token);
                    sink.accept(new AgentEvent.Token(token));
                })
                .onCompleteResponse(response -> {
                    String text = fullResponse.toString();
                    sink.accept(new AgentEvent.AgentFinished(
                            ASSISTANT_NAME, text, System.currentTimeMillis() - started));
                    sink.accept(new AgentEvent.Completed(text));
                })
                .onError(error -> sink.accept(new AgentEvent.Failed(ASSISTANT_NAME, error)))
                .start();
    }
}
