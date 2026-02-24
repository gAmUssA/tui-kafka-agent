package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;

/**
 * Builds the AnthropicStreamingChatModel from application configuration.
 */
public final class AnthropicConfig {

    private AnthropicConfig() {
    }

    public static AnthropicStreamingChatModel createStreamingModel(AppConfig config) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(config.getAnthropicApiKey())
                .modelName(config.getAnthropicModel())
                .maxTokens(config.getAnthropicMaxTokens())
                .cacheSystemMessages(config.isCacheSystemMessages())
                .cacheTools(config.isCacheTools())
                .build();
    }
}
