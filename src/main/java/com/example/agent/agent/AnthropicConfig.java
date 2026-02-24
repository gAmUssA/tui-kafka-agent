package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;

/**
 * Builds the AnthropicStreamingChatModel from application configuration.
 */
public final class AnthropicConfig {

    private static final int THINKING_BUDGET_TOKENS = 10000;

    private AnthropicConfig() {
    }

    public static AnthropicStreamingChatModel createStreamingModel(AppConfig config) {
        return createStreamingModel(config, null, false);
    }

    public static AnthropicStreamingChatModel createStreamingModel(AppConfig config, String modelOverride) {
        return createStreamingModel(config, modelOverride, false);
    }

    public static AnthropicStreamingChatModel createStreamingModel(
            AppConfig config, String modelOverride, boolean thinkingEnabled) {
        String modelName = modelOverride != null ? modelOverride : config.getAnthropicModel();
        var builder = AnthropicStreamingChatModel.builder()
                .apiKey(config.getAnthropicApiKey())
                .modelName(modelName)
                .cacheSystemMessages(config.isCacheSystemMessages())
                .cacheTools(config.isCacheTools());

        if (thinkingEnabled) {
            builder.thinkingType("enabled")
                   .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                   .maxTokens(config.getAnthropicMaxTokens() + THINKING_BUDGET_TOKENS);
        } else {
            builder.maxTokens(config.getAnthropicMaxTokens());
        }

        return builder.build();
    }
}
