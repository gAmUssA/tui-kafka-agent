package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.time.Duration;
import java.util.List;

/**
 * Builds the AnthropicStreamingChatModel from application configuration.
 * Optionally registers a {@link ChatModelListener} (e.g. {@link UsageTracker})
 * so token usage and latency are observable per request.
 */
public final class AnthropicConfig {

    private static final int THINKING_BUDGET_TOKENS = 10000;

    private AnthropicConfig() {
    }

    public static AnthropicStreamingChatModel createStreamingModel(AppConfig config) {
        return createStreamingModel(config, null, false, null);
    }

    public static AnthropicStreamingChatModel createStreamingModel(AppConfig config, String modelOverride) {
        return createStreamingModel(config, modelOverride, false, null);
    }

    public static AnthropicStreamingChatModel createStreamingModel(
            AppConfig config, String modelOverride, boolean thinkingEnabled) {
        return createStreamingModel(config, modelOverride, thinkingEnabled, null);
    }

    /**
     * Non-streaming model for agentic sub-agents and the supervisor planner
     * (langchain4j-agentic only streams from the last agent in a system).
     */
    public static AnthropicChatModel createChatModel(
            AppConfig config, String modelOverride, boolean thinkingEnabled, ChatModelListener listener) {
        String modelName = modelOverride != null ? modelOverride : config.getAnthropicModel();
        var builder = AnthropicChatModel.builder()
                .apiKey(config.getAnthropicApiKey())
                .modelName(modelName)
                .timeout(Duration.ofSeconds(config.getAnthropicTimeoutSeconds()))
                .cacheSystemMessages(config.isCacheSystemMessages())
                .cacheTools(config.isCacheTools());

        if (thinkingEnabled) {
            builder.thinkingType("enabled")
                   .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                   .maxTokens(config.getAnthropicMaxTokens() + THINKING_BUDGET_TOKENS);
        } else {
            builder.maxTokens(config.getAnthropicMaxTokens());
        }

        if (listener != null) {
            builder.listeners(List.of(listener));
        }

        return builder.build();
    }

    public static AnthropicStreamingChatModel createStreamingModel(
            AppConfig config, String modelOverride, boolean thinkingEnabled, ChatModelListener listener) {
        String modelName = modelOverride != null ? modelOverride : config.getAnthropicModel();
        var builder = AnthropicStreamingChatModel.builder()
                .apiKey(config.getAnthropicApiKey())
                .modelName(modelName)
                .timeout(Duration.ofSeconds(config.getAnthropicTimeoutSeconds()))
                .cacheSystemMessages(config.isCacheSystemMessages())
                .cacheTools(config.isCacheTools());

        if (thinkingEnabled) {
            builder.thinkingType("enabled")
                   .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                   .maxTokens(config.getAnthropicMaxTokens() + THINKING_BUDGET_TOKENS);
        } else {
            builder.maxTokens(config.getAnthropicMaxTokens());
        }

        if (listener != null) {
            builder.listeners(List.of(listener));
        }

        return builder.build();
    }
}
