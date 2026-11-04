package com.example.agent.agent;

/**
 * Supported chat model providers. The lowercase name is the canonical form
 * used in config files, slash commands ({@code /model ollama:qwen2.5}), and
 * the header bar.
 */
public enum Provider {
    ANTHROPIC,
    OLLAMA,
    /** Open-weight models on Fireworks AI, through its OpenAI-compatible API. */
    FIREWORKS,
    OPENAI;

    /** True for providers reached through the OpenAI-compatible client. */
    public boolean isOpenAiCompatible() {
        return this == FIREWORKS || this == OPENAI;
    }

    /**
     * Parse a provider name from user input or config.
     * Case-insensitive. Returns {@link #ANTHROPIC} as the default for null/blank.
     *
     * @throws IllegalArgumentException if the value is non-blank but unknown
     */
    public static Provider fromString(String s) {
        if (s == null || s.isBlank()) {
            return ANTHROPIC;
        }
        return switch (s.toLowerCase()) {
            case "anthropic" -> ANTHROPIC;
            case "ollama" -> OLLAMA;
            case "fireworks" -> FIREWORKS;
            case "openai" -> OPENAI;
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + s + " (supported: anthropic, ollama, fireworks, openai)");
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
