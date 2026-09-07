package com.example.agent.agentic;

import com.example.agent.agent.AgentEvent;
import com.example.agent.agent.ChatModelFactory;
import com.example.agent.agent.Provider;
import com.example.agent.agent.UsageTracker;
import com.example.agent.config.AppConfig;
import com.example.agent.tools.McpBridge;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorContextStrategy;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skills;

import java.util.ArrayList;
import java.util.List;
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
    // A legitimate demo turn needs at most 2–3 specialist calls. A low cap bounds the
    // damage when the planner mis-routes and keeps retrying (seen: 3× flink for a delete).
    private static final int MAX_AGENT_INVOCATIONS = 4;
    private static final int MAX_VERIFY_ITERATIONS = 3;
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
        ToolProvider flinkReadTools = (mcpBridge != null) ? mcpBridge.getToolProvider(ToolScopes.FLINK_READ) : null;
        ToolProvider flinkSubmitTools = (mcpBridge != null) ? mcpBridge.getToolProvider(ToolScopes.FLINK_SUBMIT) : null;
        ToolProvider flinkOpsTools = (mcpBridge != null) ? mcpBridge.getToolProvider(ToolScopes.FLINK_OPS) : null;

        // Agent Skills: SKILL.md bundles under resources/skills (flink-sql from the
        // gamussa/flink-sql tile). activate_skill / read_skill_resource tools give the
        // Flink agents the full guidance only when they need it.
        Skills skills = Skills.from(ClassPathSkillLoader.loadSkills("skills"));
        String skillCatalogue = "\n\nSkills available to you:\n" + skills.formatAvailableSkills();

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

        Agents.FlinkAgent flink = buildFlinkWorkflow(
                config, workerModel, flinkReadTools, flinkSubmitTools, skills, skillCatalogue);

        var flinkOpsBuilder = AgenticServices.agentBuilder(Agents.FlinkOpsAgent.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "flink-ops"))
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS);
        if (flinkOpsTools != null) {
            flinkOpsBuilder.toolProvider(flinkOpsTools);
        }
        Agents.FlinkOpsAgent flinkOps = flinkOpsBuilder.build();

        // --- supervisor -----------------------------------------------------
        Agents.BrewOpsSupervisor supervisor = AgenticServices.supervisorBuilder(Agents.BrewOpsSupervisor.class)
                .chatModel(plannerModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW))
                .subAgents(brewmaster, confluent, flinkOps, flink)
                .supervisorContext(Prompts.load(config, "supervisor-context"))
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY_AND_SUMMARIZATION)
                .maxAgentsInvocations(MAX_AGENT_INVOCATIONS)
                .build();

        // --- presenter (last ⇒ streams) --------------------------------------
        Agents.Presenter presenter = AgenticServices.agentBuilder(Agents.Presenter.class)
                .streamingChatModel(presenterModel)
                .systemMessage(Prompts.load(config, "presenter"))
                // Hand the presenter the real artifacts of this turn so it never
                // re-derives SQL from a prose summary (it did, and got it wrong).
                .context(AgentSystemFactory::presenterContext)
                .build();

        // --- root -----------------------------------------------------------
        // Root sequence and presenter are plumbing; their lifecycle is not a story beat
        // (the presenter's output is the streamed answer itself).
        var listener = new UiAgentListener(sink, Set.of(Agents.ROOT, Agents.PRESENTER));
        return AgenticServices.sequenceBuilder(Agents.BrewerySystem.class)
                .subAgents(supervisor, presenter)
                .outputKey("answer")
                // The scope persists across turns; per-turn artifacts must not leak
                // into the next turn's presenter context.
                .beforeCall(scope -> {
                    scope.writeState("statement", FlinkTypes.FlinkStatement.none());
                    scope.writeState("flinkResult", "");
                })
                .listener(listener)
                .listener(monitor)
                .build();
    }

    /**
     * Where the Flink tables live. Confluent Cloud Flink names catalogs after the
     * environment and databases after the Kafka cluster (display names, not IDs);
     * when the MCP server's default resolves to IDs, a bare table name fails and the
     * agents waste calls discovering the right qualification.
     */
    private static String flinkLocationHint(AppConfig config) {
        String catalog = config.getFlinkCatalogName();
        String database = config.getFlinkDatabaseName();
        if (catalog == null && database == null) {
            return "";
        }
        var sb = new StringBuilder("\n\nTable location (mandatory): pass ");
        if (catalog != null) {
            sb.append("catalogName=\"").append(catalog).append("\" ");
        }
        if (database != null) {
            sb.append("databaseName=\"").append(database).append("\" ");
        }
        sb.append("to every describe-flink-table / get-flink-table-info / list-flink-tables call, ")
          .append("with the bare topic name as tableName. In SQL, refer to tables by their bare ")
          .append("backtick-quoted name (the statement runs in that catalog/database); never call ")
          .append("list-flink-catalogs or list-flink-databases.");
        return sb.toString();
    }

    /** Verbatim artifacts from this turn, appended to the presenter's input. */
    private static String presenterContext(AgenticScope scope) {
        var sb = new StringBuilder();
        Object st = scope.readState("statement");
        if (st instanceof FlinkTypes.FlinkStatement statement && !statement.isEmpty()) {
            sb.append("Flink SQL submitted this turn (name: ").append(statement.name())
              .append("). If you show SQL, reproduce it EXACTLY as below, character for character:\n")
              .append(statement.sql()).append("\n\n");
        }
        Object fr = scope.readState("flinkResult");
        if (fr instanceof String s && !s.isBlank()) {
            sb.append("Flink submitter report:\n").append(s).append("\n");
        }
        return sb.toString();
    }

    /**
     * The Flink specialist as a workflow:
     * {@code sequence( loop(author → critic) until criticResult.successful, submitter )}.
     * The loop exits on the critic's typed verdict; the submitter only ever sees an
     * approved {@link FlinkTypes.FlinkStatement}.
     */
    private static Agents.FlinkAgent buildFlinkWorkflow(
            AppConfig config,
            ChatModel workerModel,
            ToolProvider readTools,
            ToolProvider writeTools,
            Skills skills,
            String skillCatalogue) {

        List<ToolProvider> authoringTools = new ArrayList<>();
        authoringTools.add(skills.toolProvider());
        if (readTools != null) {
            authoringTools.add(readTools);
        }

        String flinkLocation = flinkLocationHint(config);

        FlinkAgents.SqlAuthor author = AgenticServices.agentBuilder(FlinkAgents.SqlAuthor.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "sql-author") + flinkLocation + skillCatalogue)
                .toolProviders(authoringTools)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();

        FlinkAgents.SqlCritic critic = AgenticServices.agentBuilder(FlinkAgents.SqlCritic.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "sql-critic") + flinkLocation + skillCatalogue)
                .toolProviders(authoringTools)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();

        var submitterBuilder = AgenticServices.agentBuilder(FlinkAgents.SqlSubmitter.class)
                .chatModel(workerModel)
                .systemMessage(Prompts.load(config, "sql-submitter"))
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS);
        if (writeTools != null) {
            submitterBuilder.toolProvider(writeTools);
        }
        FlinkAgents.SqlSubmitter submitter = submitterBuilder.build();

        var verifyLoop = AgenticServices.loopBuilder()
                .name("sqlVerifyLoop")
                .subAgents(author, critic)
                .maxIterations(MAX_VERIFY_ITERATIONS)
                .exitCondition("the critic approved the statement",
                        scope -> scope.readState("criticResult", FlinkTypes.CriticResult.pending()).successful())
                .build();

        return AgenticServices.sequenceBuilder(Agents.FlinkAgent.class)
                .subAgents(verifyLoop, submitter)
                .outputKey("flinkResult")
                // The scope persists across turns: start every Flink task from a clean verdict.
                .beforeCall(scope -> scope.writeState("criticResult", FlinkTypes.CriticResult.pending()))
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
        // Logged, not printed: stderr is the terminal the TUI paints on.
        org.slf4j.LoggerFactory.getLogger(AgentSystemFactory.class)
                .warn("No Anthropic key; using {} as supervisor planner (expect weaker plans).", provider);
        return ChatModelFactory.createBlocking(config, provider, modelOverride, false, usage);
    }
}
