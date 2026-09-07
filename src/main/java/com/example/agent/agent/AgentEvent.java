package com.example.agent.agent;

import java.util.function.Consumer;

/**
 * Everything the agent layer wants the UI to know, as one sealed hierarchy.
 * <p>
 * This is the seam between the LangChain4j side (single {@code TokenStream}
 * today, a multi-agent {@code langchain4j-agentic} system next) and whatever
 * renders it (tui4j today, TamboUI next). It deliberately has no UI imports:
 * a UI adapts events onto its own event loop, e.g. {@code program.send(...)}
 * or {@code runner.runOnRenderThread(...)}.
 * <p>
 * Events are published from LangChain4j callback threads; consumers must
 * marshal onto their own thread if they need to.
 */
public sealed interface AgentEvent {

    /** A sub-agent (or the single assistant) started working on the turn. */
    record AgentStarted(String agent) implements AgentEvent { }

    /** A sub-agent finished; {@code output} may be null for streaming agents. */
    record AgentFinished(String agent, Object output, long durationMs) implements AgentEvent { }

    /** A tool call is about to execute. {@code agent} may be null when unknown. */
    record ToolStarted(String agent, String tool, String arguments) implements AgentEvent { }

    /** A tool call returned. */
    record ToolFinished(String agent, String tool, String result) implements AgentEvent { }

    /** One streamed token of the user-facing answer. */
    record Token(String text) implements AgentEvent { }

    /** The user-facing answer is complete. */
    record Completed(String fullText) implements AgentEvent { }

    /** The turn failed. {@code agent} may be null. */
    record Failed(String agent, Throwable error) implements AgentEvent {
        public String message() {
            String m = error.getMessage();
            return (m == null || m.isBlank()) ? error.getClass().getSimpleName() : m;
        }
    }

    /** Where events go. A UI supplies one; the agent layer only ever calls {@code accept}. */
    @FunctionalInterface
    interface Sink extends Consumer<AgentEvent> { }
}
