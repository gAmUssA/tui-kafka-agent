package com.example.agent.agentic;

import com.example.agent.agent.AgentEvent;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns langchain4j-agentic lifecycle callbacks into {@link AgentEvent}s.
 * Registered on the root agent with {@code inheritedBySubagents() = true} so
 * every hand-off and tool call at any depth reaches the UI.
 * <p>
 * Callbacks run on the invoking thread — publish and return, never block.
 */
final class UiAgentListener implements AgentListener {

    private final AgentEvent.Sink sink;
    /** Agents whose lifecycle is plumbing, not a story beat (root sequence). */
    private final Set<String> silent;
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    UiAgentListener(AgentEvent.Sink sink, Set<String> silentAgentNames) {
        this.sink = sink;
        this.silent = silentAgentNames;
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        String name = request.agentName();
        if (silent.contains(name)) {
            return;
        }
        startedAt.put(request.agentId(), System.currentTimeMillis());
        sink.accept(new AgentEvent.AgentStarted(name));
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        String name = response.agentName();
        if (silent.contains(name)) {
            return;
        }
        Long t0 = startedAt.remove(response.agentId());
        long ms = (t0 == null) ? 0 : System.currentTimeMillis() - t0;
        sink.accept(new AgentEvent.AgentFinished(name, response.output(), ms));
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution e) {
        var req = e.toolExecution().request();
        sink.accept(new AgentEvent.ToolStarted(e.agentInstance().name(), req.name(), req.arguments()));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution e) {
        var exec = e.toolExecution();
        sink.accept(new AgentEvent.ToolFinished(
                e.agentInstance().name(), exec.request().name(), exec.result()));
    }

    // Errors are reported once, by AgenticBridge, from the exception that
    // propagates out of the root invocation — not here, to avoid duplicates.
}
