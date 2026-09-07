package com.example.agent.agentic;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.ConversationBridge;
import dev.langchain4j.service.TokenStream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives one turn through the multi-agent {@link Agents.BrewerySystem}.
 * <p>
 * The root invocation blocks while the supervisor and specialists run (their
 * progress arrives via {@link UiAgentListener}); only the presenter's
 * {@link TokenStream} comes back, which is then started and forwarded as
 * {@link AgentEvent.Token}s. So the call runs on a virtual thread.
 */
public final class AgenticBridge implements ConversationBridge {

    private static final String SESSION = "default";

    private final Agents.BrewerySystem system;
    private final AgentEvent.Sink sink;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgenticBridge(Agents.BrewerySystem system, AgentEvent.Sink sink) {
        this.system = system;
        this.sink = sink;
    }

    @Override
    public String modeLabel() {
        return "agentic";
    }

    @Override
    public void reset() {
        try {
            system.evictAgenticScope(SESSION);
        } catch (RuntimeException ignored) {
            // nothing persisted yet
        }
    }

    @Override
    public void sendMessage(String userInput) {
        executor.submit(() -> run(userInput));
    }

    private void run(String userInput) {
        TokenStream stream;
        try {
            stream = system.chat(SESSION, userInput);
        } catch (RuntimeException e) {
            sink.accept(new AgentEvent.Failed(Agents.SUPERVISOR, unwrap(e)));
            return;
        }

        StringBuilder full = new StringBuilder();
        stream
                .onPartialResponse(token -> {
                    full.append(token);
                    sink.accept(new AgentEvent.Token(token));
                })
                .onCompleteResponse(r -> sink.accept(new AgentEvent.Completed(full.toString())))
                .onError(err -> sink.accept(new AgentEvent.Failed(Agents.PRESENTER, err)))
                .start();
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur
                && (cur.getMessage() == null || cur.getMessage().isBlank())) {
            cur = cur.getCause();
        }
        return cur;
    }
}
