package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * Provider-agnostic dispatcher that builds a {@link StreamingChatModel} for
 * the requested {@link Provider}. Keeps the rest of the app free of
 * provider-specific imports — callers just hand in a provider + model name
 * and get back a streaming model that fits the LangChain4j AiServices builder.
 */
public final class ChatModelFactory {

    private ChatModelFactory() {
    }

    public static StreamingChatModel create(
            AppConfig config, Provider provider, String modelOverride, boolean thinkingEnabled) {
        return switch (provider) {
            case ANTHROPIC -> AnthropicConfig.createStreamingModel(config, modelOverride, thinkingEnabled);
            case OLLAMA -> OllamaConfig.createStreamingModel(config, modelOverride);
        };
    }
}
