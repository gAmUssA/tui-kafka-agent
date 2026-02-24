package com.example.agent.agent;

import java.util.Map;

/**
 * Maps model shorthand names to full Anthropic model identifiers.
 */
public final class ModelSwitcher {

    private static final Map<String, String> MODEL_MAP = Map.of(
            "sonnet", "claude-sonnet-4-20250514",
            "haiku", "claude-haiku-4-20250414",
            "opus", "claude-opus-4-20250514"
    );

    private ModelSwitcher() {
    }

    public static String resolve(String shortName) {
        return MODEL_MAP.getOrDefault(shortName.toLowerCase(), null);
    }

    public static String availableModels() {
        return String.join(", ", MODEL_MAP.keySet());
    }
}
