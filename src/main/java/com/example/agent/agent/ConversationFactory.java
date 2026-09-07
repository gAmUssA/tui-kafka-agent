package com.example.agent.agent;

import com.example.agent.agentic.AgentSystemFactory;
import com.example.agent.agentic.AgenticBridge;
import com.example.agent.config.AppConfig;
import com.example.agent.tools.McpBridge;
import dev.langchain4j.agentic.observability.AgentMonitor;

/**
 * Builds a {@link ConversationBridge} for the current settings. Owns the
 * session-scoped {@link UsageTracker} and {@link AgentMonitor} so they survive
 * every rebuild ({@code /model}, {@code /thinking}, {@code /mcp}, {@code /agentic}).
 */
public final class ConversationFactory {

    private final AppConfig config;
    private final AgentEvent.Sink sink;
    private final UsageTracker usageTracker;
    private final AgentMonitor monitor = new AgentMonitor();

    public ConversationFactory(AppConfig config, AgentEvent.Sink sink, UsageTracker usageTracker) {
        this.config = config;
        this.sink = sink;
        this.usageTracker = usageTracker;
    }

    public ConversationBridge create(
            McpBridge mcpBridge,
            Provider provider,
            String modelOverride,
            boolean thinkingEnabled,
            boolean agentic) {
        if (agentic) {
            var system = AgentSystemFactory.create(
                    config, connected(mcpBridge), provider, modelOverride, thinkingEnabled,
                    usageTracker, sink, monitor);
            return new AgenticBridge(system, sink);
        }
        var tp = connected(mcpBridge) != null ? mcpBridge.getToolProvider() : null;
        AgentAssistant assistant = AgentFactory.create(
                config, tp, provider, modelOverride, thinkingEnabled, usageTracker);
        return new StreamBridge(sink, assistant);
    }

    public AgentMonitor monitor() {
        return monitor;
    }

    public UsageTracker usageTracker() {
        return usageTracker;
    }

    private static McpBridge connected(McpBridge bridge) {
        return (bridge != null && bridge.isConnected()) ? bridge : null;
    }
}
