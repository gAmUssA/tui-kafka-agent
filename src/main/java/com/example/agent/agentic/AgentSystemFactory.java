package com.example.agent.agentic;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.ChatModelFactory;
import com.example.agent.agent.Provider;
import com.example.agent.agent.UsageTracker;
import com.example.agent.config.AppConfig;
import com.example.agent.tools.McpBridge;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.tool.ToolProvider;

import java.util.Set;

/**
 * Wires the brewery multi-agent system:
 * <pre>
 *   BrewerySystem = sequence( BrewOpsSupervisor( brewmaster, confluent, flink ), presenter )
 * </pre>
 * The supervisor plans with a blocking Anthropic model; specialists use the
 * active provider/model; the presenter is the only streaming agent and sits
 * last so its {@code TokenStream} reaches the UI.
 */
public final class AgentSystemFactory {

    private static final int MAX_TOOL_ROUND_TRIPS = 12;
    private static final int MAX_AGENT_INVOCATIONS = 8;
    private static final int MEMORY_WINDOW = 30;

    private AgentSystemFactory() {
    }

    public static Agents.BrewerySystem create(
            AppConfig config,
            McpBridge mcpBridge,
            Provider provider,
            String modelOverride,
            boolean thinkingEnabled,
            UsageTracker usage,
            AgentEvent.Sink sink,
            AgentMonitor monitor) {

        // --- models -------------------------------------------------------
        ChatModel workerModel = ChatModelFactory.createBlocking(
                config, provider, modelOverride, thinkingEnabled, usage);
        ChatModel plannerModel = plannerModel(config, provider, modelOverride, usage);
        StreamingChatModel presenterModel = ChatModelFactory.create(
                config, provider, modelOverride, false, usage);

        // --- tools, scoped per specialist ---------------------------------
        ToolProvider confluentTools = (mcpBridge != null) ? mcpBridge.getToolProvider(ToolScopes.CONFLUENT) : null;
        ToolProvider flinkTools = (mcpBridge != null) ? mcpBridge.getToolProvider(ToolScopes.FLINK) : null;

        // --- specialists ----------------------------------------------------
        var brewmasterBuilder = AgenticServices.agentBuilder(Agents.BrewmasterAgent.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "brewmaster"));
        Agents.BrewmasterAgent brewmaster = brewmasterBuilder.build();

        var confluentBuilder = AgenticServices.agentBuilder(Agents.ConfluentAgent.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "confluent"))
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS);
        if (confluentTools != null) {
            confluentBuilder.toolProvider(confluentTools);
        }
        Agents.ConfluentAgent confluent = confluentBuilder.build();

        var flinkBuilder = AgenticServices.agentBuilder(Agents.FlinkAgent.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "flink"))
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS);
        if (flinkTools != null) {
            flinkBuilder.toolProvider(flinkTools);
        }
        Agents.FlinkAgent flink = flinkBuilder.build();

        // --- supervisor -----------------------------------------------------
        Agents.BrewOpsSupervisor supervisor = AgenticServices.supervisorBuilder(Agents.BrewOpsSupervisor.class)
                .chatModel(plannerModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW))
                .subAgents(brewmaster, confluent, flink)
                .supervisorContext(Prompts.load(config, "supervisor-context"))
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                .maxAgentsInvocations(MAX_AGENT_INVOCATIONS)
                .build();

        // --- presenter (last ⇒ streams) --------------------------------------
        Agents.Presenter presenter = AgenticServices.agentBuilder(Agents.Presenter.class)
                .streamingChatModel(presenterModel)
                .systemMessage(Prompts.load(config, "presenter"))
                .build();

        // --- root -----------------------------------------------------------
        // Root sequence and presenter are plumbing; their lifecycle is not a story beat
        // (the presenter's output is the streamed answer itself).
        var listener = new UiAgentListener(sink, Set.of(Agents.ROOT, Agents.PRESENTER));
        return AgenticServices.sequenceBuilder(Agents.BrewerySystem.class)
                .subAgents(supervisor, presenter)
                .outputKey("answer")
                .listener(listener)
                .listener(monitor)
                .build();
    }

    /**
     * The planner is always Anthropic when a key is configured — local models
     * plan poorly. Falls back to the worker provider otherwise.
     */
    private static ChatModel plannerModel(AppConfig config, Provider provider, String modelOverride, UsageTracker usage) {
        boolean haveAnthropic = !config.getAnthropicApiKey().isBlank();
        if (haveAnthropic) {
            return ChatModelFactory.createBlocking(
                    config, Provider.ANTHROPIC, config.getAgenticPlannerModel(), false, usage);
        }
        System.err.println("[agentic] No Anthropic key; using " + provider + " as supervisor planner (expect weaker plans).");
        return ChatModelFactory.createBlocking(config, provider, modelOverride, false, usage);
    }
}
