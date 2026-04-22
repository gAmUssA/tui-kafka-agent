package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

/**
 * Wires the AiServices builder with a provider-agnostic streaming chat model
 * and chat memory. Supports rebuilding with additional tool providers
 * (e.g., from MCP) and switching between providers (Anthropic, Ollama).
 */
public final class AgentFactory {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are kafka-agent, a terminal-based AI assistant specialized in Apache Kafka \
            and Flink on Confluent Cloud.

            Your primary capabilities:
            - Explain Kafka concepts (topics, partitions, consumer groups, offsets, replication)
            - Help with Flink SQL queries for stream processing
            - Assist with Confluent Cloud configuration and troubleshooting
            - Guide users through common Kafka operations

            You have tools available via MCP servers that the user can connect. \
            Use them when the user asks about their Kafka topics, Flink jobs, or cluster state.

            Keep responses concise and terminal-friendly. Use short paragraphs. \
            When showing code or configs, use plain text (no markdown fences — this is a terminal). \
            If you don't know something, say so rather than guessing.""";

    private AgentFactory() {
    }

    // ------------------------------------------------------------------
    // Convenience overloads — default to the configured provider
    // ------------------------------------------------------------------

    public static AgentAssistant create(AppConfig config) {
        return create(config, null, config.getProvider(), null, false);
    }

    public static AgentAssistant create(AppConfig config, ToolProvider toolProvider) {
        return create(config, toolProvider, config.getProvider(), null, false);
    }

    public static AgentAssistant create(
            AppConfig config, ToolProvider toolProvider, String modelOverride) {
        return create(config, toolProvider, config.getProvider(), modelOverride, false);
    }

    public static AgentAssistant create(
            AppConfig config, ToolProvider toolProvider, String modelOverride, boolean thinkingEnabled) {
        return create(config, toolProvider, config.getProvider(), modelOverride, thinkingEnabled);
    }

    // ------------------------------------------------------------------
    // Primary constructor — explicit provider
    // ------------------------------------------------------------------

    public static AgentAssistant create(
            AppConfig config,
            ToolProvider toolProvider,
            Provider provider,
            String modelOverride,
            boolean thinkingEnabled) {

        StreamingChatModel model =
                ChatModelFactory.create(config, provider, modelOverride, thinkingEnabled);

        var builder = AiServices.builder(AgentAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .systemMessageProvider(memoryId -> {
                    String custom = config.getSystemPrompt();
                    return custom != null ? custom : DEFAULT_SYSTEM_PROMPT;
                });

        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }

        return builder.build();
    }
}
