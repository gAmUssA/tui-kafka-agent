package com.example.agent.agent;

import com.example.agent.config.AppConfig;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;

import java.time.Duration;
import java.util.List;

/**
 * Builds an {@link OllamaStreamingChatModel} from application configuration.
 * Talks to a locally-running Ollama server (default: {@code http://localhost:11434}).
 * <p>
 * Tool calling is delegated to the underlying model — only Ollama models that
 * natively support function calling (qwen2.5, llama3.1+, mistral-nemo, etc.)
 * will invoke MCP tools. Models without tool support will silently ignore the
 * tool definitions and respond from prompt only.
 */
public final class OllamaConfig {

    private OllamaConfig() {
    }

    public static OllamaStreamingChatModel createStreamingModel(AppConfig config) {
        return createStreamingModel(config, null, null);
    }

    public static OllamaStreamingChatModel createStreamingModel(AppConfig config, String modelOverride) {
        return createStreamingModel(config, modelOverride, null);
    }

    public static OllamaStreamingChatModel createStreamingModel(
            AppConfig config, String modelOverride, ChatModelListener listener) {
        String modelName = modelOverride != null ? modelOverride : config.getOllamaModel();
        var builder = OllamaStreamingChatModel.builder()
                .baseUrl(config.getOllamaBaseUrl())
                .modelName(modelName)
                .timeout(Duration.ofSeconds(config.getOllamaTimeoutSeconds()));

        if (listener != null) {
            builder.listeners(List.of(listener));
        }

        return builder.build();
    }
}
