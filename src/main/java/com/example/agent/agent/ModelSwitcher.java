package com.example.agent.agent;

import java.util.Map;

/**
 * Parses {@code /model} arguments into a (provider, model-name) pair.
 * <p>
 * Syntax:
 * <ul>
 *   <li>{@code <name>} — keep the current provider, switch model only.
 *       Anthropic aliases (sonnet/haiku/opus) are expanded; everything else
 *       is passed through as a literal model identifier.</li>
 *   <li>{@code <provider>:<name>} — switch provider AND model. The prefix is
 *       only treated as a provider if it matches a known provider name
 *       ({@code anthropic} or {@code ollama}); otherwise the whole input is
 *       treated as a literal model name. This avoids breaking Ollama tags
 *       like {@code qwen2.5:7b} which contain their own colon.</li>
 * </ul>
 */
public final class ModelSwitcher {

    private static final Map<String, String> ANTHROPIC_ALIASES = Map.of(
            "sonnet", "claude-sonnet-4-6",
            "haiku", "claude-haiku-4-5",
            "opus", "claude-opus-4-7"
    );

    /** Result of parsing a /model argument: which provider to use and which model. */
    public record Resolved(Provider provider, String modelName) {
    }

    private ModelSwitcher() {
    }

    /**
     * Parse a /model argument against the current provider.
     *
     * @param input           the raw user input (e.g., {@code "opus"}, {@code "ollama:qwen2.5:7b"})
     * @param currentProvider the active provider, used for unqualified inputs
     */
    public static Resolved resolve(String input, Provider currentProvider) {
        int colon = input.indexOf(':');
        if (colon > 0) {
            String prefix = input.substring(0, colon).toLowerCase();
            if ("anthropic".equals(prefix) || "ollama".equals(prefix)) {
                Provider target = Provider.fromString(prefix);
                String name = input.substring(colon + 1);
                return new Resolved(target, resolveModelName(target, name));
            }
        }
        return new Resolved(currentProvider, resolveModelName(currentProvider, input));
    }

    private static String resolveModelName(Provider provider, String name) {
        return switch (provider) {
            case ANTHROPIC -> ANTHROPIC_ALIASES.getOrDefault(name.toLowerCase(), name);
            case OLLAMA -> name;
        };
    }

    /** Comma-separated list of Anthropic short-name aliases for help text. */
    public static String anthropicAliases() {
        return String.join(", ", ANTHROPIC_ALIASES.keySet());
    }
}
