package com.example.agent.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses {@code /model} arguments into a (provider, model-name) pair.
 * <p>
 * Syntax:
 * <ul>
 *   <li>{@code <alias>} — a short name from the alias table switches provider AND model:
 *       {@code sonnet}/{@code haiku}/{@code opus} go to Anthropic, {@code deepseek}/{@code kimi}/
 *       {@code ember}/{@code glm} go to Fireworks. This is the on-stage way to swap the whole team.</li>
 *   <li>{@code <name>} — any other name keeps the current provider and switches model only.</li>
 *   <li>{@code <provider>:<name>} — switch provider AND model. The prefix is only treated as a
 *       provider if it matches a known provider name; otherwise the whole input is a literal model
 *       name. This avoids breaking Ollama tags like {@code qwen2.5:7b} which contain their own colon.
 *       Aliases also resolve after a prefix of their own provider ({@code fireworks:kimi}).</li>
 * </ul>
 */
public final class ModelSwitcher {

    private static final String FW = "accounts/fireworks/models/";

    private static final Map<String, Resolved> ALIASES = aliases();

    /** Result of parsing a /model argument: which provider to use and which model. */
    public record Resolved(Provider provider, String modelName) {
    }

    private ModelSwitcher() {
    }

    private static Map<String, Resolved> aliases() {
        Map<String, Resolved> m = new LinkedHashMap<>();
        m.put("sonnet", new Resolved(Provider.ANTHROPIC, "claude-sonnet-4-6"));
        m.put("haiku", new Resolved(Provider.ANTHROPIC, "claude-haiku-4-5"));
        m.put("opus", new Resolved(Provider.ANTHROPIC, "claude-opus-4-7"));
        m.put("deepseek", new Resolved(Provider.FIREWORKS, FW + "deepseek-v4p1-flash"));
        m.put("kimi", new Resolved(Provider.FIREWORKS, FW + "kimi-k3"));
        m.put("ember", new Resolved(Provider.FIREWORKS, FW + "ember-1"));
        m.put("glm", new Resolved(Provider.FIREWORKS, FW + "glm-5p3-flash"));
        return Map.copyOf(m);
    }

    /**
     * Parse a /model argument against the current provider.
     *
     * @param input           the raw user input (e.g., {@code "opus"}, {@code "ollama:qwen2.5:7b"})
     * @param currentProvider the active provider, used for unqualified non-alias inputs
     * @throws IllegalArgumentException if the input is blank
     */
    public static Resolved resolve(String input, Provider currentProvider) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Model name is empty — try /model sonnet or /model deepseek");
        }
        String trimmed = input.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            Provider target = knownProvider(trimmed.substring(0, colon));
            if (target != null) {
                String name = trimmed.substring(colon + 1);
                Resolved alias = ALIASES.get(name.toLowerCase());
                if (alias != null && alias.provider() == target) {
                    return alias;
                }
                return new Resolved(target, name);
            }
        }
        Resolved alias = ALIASES.get(trimmed.toLowerCase());
        if (alias != null) {
            return alias;
        }
        return new Resolved(currentProvider, trimmed);
    }

    private static Provider knownProvider(String prefix) {
        try {
            return Provider.fromString(prefix);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Alias names for help text, e.g. {@code sonnet, haiku, opus, deepseek, kimi, ember, glm}. */
    public static String aliasNames() {
        return String.join(", ", "sonnet", "haiku", "opus", "deepseek", "kimi", "ember", "glm");
    }

    /** Last path segment of a model id, for the header ({@code accounts/fireworks/models/kimi-k3} → {@code kimi-k3}). */
    public static String shortName(String model) {
        if (model == null || model.isBlank()) {
            return "?";
        }
        for (String family : new String[] {"sonnet", "haiku", "opus"}) {
            if (model.contains(family)) {
                return family;
            }
        }
        int slash = model.lastIndexOf('/');
        return slash >= 0 ? model.substring(slash + 1) : model;
    }
}
