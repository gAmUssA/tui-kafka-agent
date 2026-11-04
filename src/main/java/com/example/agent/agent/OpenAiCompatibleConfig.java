package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import com.example.agent.config.AppConfig.OpenAiCompatibleSettings;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.List;

/**
 * Builds chat models for providers that speak the OpenAI Chat Completions API:
 * OpenAI itself and Fireworks AI (open-weight models such as DeepSeek, Kimi, Ember).
 * Only the base URL, key and model differ, so both share the LangChain4j OpenAI client.
 */
public final class OpenAiCompatibleConfig {

    private OpenAiCompatibleConfig() {
    }

    /** Non-streaming model for agentic sub-agents and the supervisor planner. */
    public static OpenAiChatModel createChatModel(
            AppConfig config, Provider provider, String modelOverride, ChatModelListener listener) {
        OpenAiCompatibleSettings s = config.getOpenAiCompatible(provider);
        var builder = OpenAiChatModel.builder()
                .baseUrl(s.baseUrl())
                .apiKey(s.apiKey())
                .modelName(modelOverride != null ? modelOverride : s.model())
                .maxTokens(s.maxTokens())
                .timeout(Duration.ofSeconds(s.timeoutSeconds()));
        if (listener != null) {
            builder.listeners(List.of(listener));
        }
        return builder.build();
    }

    public static OpenAiStreamingChatModel createStreamingModel(
            AppConfig config, Provider provider, String modelOverride, ChatModelListener listener) {
        OpenAiCompatibleSettings s = config.getOpenAiCompatible(provider);
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(s.baseUrl())
                .apiKey(s.apiKey())
                .modelName(modelOverride != null ? modelOverride : s.model())
                .maxTokens(s.maxTokens())
                .timeout(Duration.ofSeconds(s.timeoutSeconds()));
        if (listener != null) {
            builder.listeners(List.of(listener));
        }
        return builder.build();
    }
}
