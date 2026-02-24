package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Wires the AiServices builder with the streaming model and chat memory.
 */
public final class AgentFactory {

    private static final String SYSTEM_PROMPT = """
            You are kafka-agent, a terminal-based AI assistant specialized in Apache Kafka \
            and Flink on Confluent Cloud.

            Your primary capabilities:
            - Explain Kafka concepts (topics, partitions, consumer groups, offsets, replication)
            - Help with Flink SQL queries for stream processing
            - Assist with Confluent Cloud configuration and troubleshooting
            - Guide users through common Kafka operations

            When tools become available, you will be able to:
            - List and describe Kafka topics
            - Produce test messages
            - Check consumer group lag
            - Submit and monitor Flink SQL statements

            Keep responses concise and terminal-friendly. Use short paragraphs. \
            When showing code or configs, use plain text (no markdown fences — this is a terminal). \
            If you don't know something, say so rather than guessing.""";

    private AgentFactory() {
    }

    public static AgentAssistant create(AppConfig config) {
        AnthropicStreamingChatModel model = AnthropicConfig.createStreamingModel(config);

        return AiServices.builder(AgentAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .systemMessageProvider(memoryId -> SYSTEM_PROMPT)
                .build();
    }
}
