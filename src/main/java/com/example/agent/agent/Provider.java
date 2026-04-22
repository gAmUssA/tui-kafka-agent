package com.example.agent.agent;

/**
 * Supported chat model providers. The lowercase name is the canonical form
 * used in config files, slash commands ({@code /model ollama:qwen2.5}), and
 * the header bar.
 */
public enum Provider {
    ANTHROPIC,
    OLLAMA;

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
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + s + " (supported: anthropic, ollama)");
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
