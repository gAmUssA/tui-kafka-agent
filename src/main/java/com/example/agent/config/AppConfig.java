package com.example.agent.config;

import com.example.agent.agent.Provider;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application configuration loaded from YAML files with environment variable substitution.
 * <p>
 * Loading order:
 * <ol>
 *   <li>Load {@code default-config.yaml} from the classpath (bundled defaults)</li>
 *   <li>If {@code ~/.config/kafka-agent/config.yaml} exists, overlay its values on top</li>
 *   <li>Perform {@code ${ENV_VAR}} substitution on the raw YAML before parsing</li>
 * </ol>
 * <p>
 * If the user config file does not exist, a helpful message is printed to stderr
 * and the defaults (with env-var resolution) are used.
 * <p>
 * After all resolution, {@code anthropic.api-key} must be non-empty or the process exits.
 */
public final class AppConfig {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Path USER_CONFIG_PATH =
            Path.of(System.getProperty("user.home"), ".config", "kafka-agent", "config.yaml");

    private final Map<String, Object> config;

    private AppConfig(Map<String, Object> config) {
        this.config = config;
    }

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    /**
     * Load configuration from the default classpath resource, overlay with the
     * user config file (if present), substitute environment variables, and
     * validate required keys.
     *
     * @return a fully resolved {@link AppConfig}
     */
    public static AppConfig load() {
        // 1. Load bundled defaults
        Map<String, Object> defaults = loadFromClasspath("default-config.yaml");

        // 2. Attempt to load user config overlay
        Map<String, Object> userConfig = loadUserConfig();

        // 3. Merge: user values override defaults
        Map<String, Object> merged = deepMerge(defaults, userConfig);

        AppConfig appConfig = new AppConfig(merged);

        // 4. Validate required keys
        appConfig.validate();

        return appConfig;
    }

    // ------------------------------------------------------------------
    // Typed getters — Provider selection
    // ------------------------------------------------------------------

    /**
     * Returns the active chat model provider. Defaults to {@link Provider#ANTHROPIC}
     * if {@code provider} is unset or blank in config. Throws
     * {@link IllegalArgumentException} if the configured value is not recognized.
     */
    public Provider getProvider() {
        return Provider.fromString(getStringValue("provider", "anthropic"));
    }

    // ------------------------------------------------------------------
    // Typed getters — Ollama
    // ------------------------------------------------------------------

    public String getOllamaBaseUrl() {
        return getStringValue("ollama.base-url", "http://localhost:11434");
    }

    public String getOllamaModel() {
        return getStringValue("ollama.model", "qwen2.5:7b");
    }

    public int getOllamaTimeoutSeconds() {
        // Default: 10 minutes. Local inference is slow under load — model
        // load on first call, agentic tool loops issuing several round-trips,
        // verbose contexts on small models. Per-call timeout, not per-session.
        return getIntValue("ollama.timeout-seconds", 600);
    }

    // ------------------------------------------------------------------
    // Typed getters — Anthropic
    // ------------------------------------------------------------------

    public String getAnthropicApiKey() {
        return getStringValue("anthropic.api-key", "");
    }

    public String getAnthropicModel() {
        return getStringValue("anthropic.model", "claude-sonnet-4-6");
    }

    public int getAnthropicMaxTokens() {
        return getIntValue("anthropic.max-tokens", 4096);
    }

    public boolean isCacheSystemMessages() {
        return getBooleanValue("anthropic.cache-system-messages", true);
    }

    public boolean isCacheTools() {
        return getBooleanValue("anthropic.cache-tools", true);
    }

    public boolean isThinkingEnabled() {
        return getBooleanValue("anthropic.thinking.enabled", false);
    }

    public int getThinkingBudgetTokens() {
        return getIntValue("anthropic.thinking.budget-tokens", 2048);
    }

    // ------------------------------------------------------------------
    // Typed getters — Confluent
    // ------------------------------------------------------------------

    public String getConfluentBootstrap() {
        return getStringValue("confluent.bootstrap-servers", "");
    }

    public String getConfluentApiKey() {
        return getStringValue("confluent.api-key", "");
    }

    public String getConfluentApiSecret() {
        return getStringValue("confluent.api-secret", "");
    }

    public String getFlinkEnvironmentId() {
        return getStringValue("confluent.flink.environment-id", "");
    }

    public String getFlinkOrgId() {
        return getStringValue("confluent.flink.org-id", "");
    }

    // ------------------------------------------------------------------
    // Typed getters — App branding & profile
    // ------------------------------------------------------------------

    public String getAppName() {
        return getStringValue("app.name", "kafka-agent");
    }

    /**
     * Returns the custom system prompt from config, or {@code null} if not set
     * (signaling the caller to use its built-in default).
     */
    public String getSystemPrompt() {
        Object value = getNestedValue("app.system-prompt");
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return str.isBlank() ? null : str;
    }

    public String getMcpAutoConnectUrl() {
        return getStringValue("mcp.auto-connect-url", "");
    }

    /**
     * Returns configured MCP servers as a map of name → {@link McpServerConfig}.
     * <p>
     * Supports the new {@code mcp.servers} map format. Falls back to legacy
     * {@code mcp.auto-connect-url} as a single SSE server named "default"
     * with a deprecation warning.
     */
    @SuppressWarnings("unchecked")
    public Map<String, McpServerConfig> getMcpServers() {
        Object serversObj = getNestedValue("mcp.servers");
        if (serversObj instanceof Map<?, ?> serversMap && !serversMap.isEmpty()) {
            Map<String, McpServerConfig> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : serversMap.entrySet()) {
                String name = entry.getKey().toString();
                if (entry.getValue() instanceof Map<?, ?> serverDef) {
                    Object typeObj = serverDef.get("type");
                    String type = (typeObj != null) ? typeObj.toString() : "sse";
                    String url = serverDef.containsKey("url") ? serverDef.get("url").toString() : null;

                    List<String> command = null;
                    Object cmdObj = serverDef.get("command");
                    if (cmdObj instanceof List<?> cmdList) {
                        command = cmdList.stream().map(Object::toString).toList();
                    }

                    Map<String, String> env = Collections.emptyMap();
                    Object envObj = serverDef.get("env");
                    if (envObj instanceof Map<?, ?> envMap) {
                        Map<String, String> envResult = new LinkedHashMap<>();
                        envMap.forEach((k, v) -> envResult.put(k.toString(), v.toString()));
                        env = envResult;
                    }

                    result.put(name, new McpServerConfig(type, url, command, env));
                }
            }
            return result;
        }

        // Backward compat: legacy mcp.auto-connect-url
        String legacyUrl = getMcpAutoConnectUrl();
        if (!legacyUrl.isEmpty()) {
            System.err.println("[config] DEPRECATED: 'mcp.auto-connect-url' is deprecated. "
                    + "Use 'mcp.servers' map instead. See default-config.yaml for the new format.");
            return Map.of("default", new McpServerConfig("sse", legacyUrl, null, Collections.emptyMap()));
        }

        return Collections.emptyMap();
    }

    // ------------------------------------------------------------------
    // Typed getters — Demo commands
    // ------------------------------------------------------------------

    public String getDemoSetupPrompt() {
        Object value = getNestedValue("demo.setup");
        if (value == null) return null;
        String str = value.toString();
        return str.isBlank() ? null : str.strip();
    }

    public String getDemoResetPrompt() {
        Object value = getNestedValue("demo.reset");
        if (value == null) return null;
        String str = value.toString();
        return str.isBlank() ? null : str.strip();
    }

    // ------------------------------------------------------------------
    // Internal — loading helpers
    // ------------------------------------------------------------------

    /**
     * Load a YAML resource from the classpath, performing env-var substitution
     * on the raw text before parsing.
     */
    private static Map<String, Object> loadFromClasspath(String resourceName) {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                System.err.println("[config] WARNING: classpath resource '" + resourceName + "' not found; using empty defaults.");
                return new LinkedHashMap<>();
            }
            String raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return parseYaml(substituteEnvVars(raw));
        } catch (IOException e) {
            System.err.println("[config] WARNING: failed to read classpath resource '" + resourceName + "': " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Load the user config file at {@code ~/.config/kafka-agent/config.yaml}.
     * If the file does not exist, print a helpful message and return an empty map.
     */
    private static Map<String, Object> loadUserConfig() {
        if (!Files.exists(USER_CONFIG_PATH)) {
            System.err.println("[config] User config not found at " + USER_CONFIG_PATH);
            System.err.println("[config] Falling back to environment variables and bundled defaults.");
            System.err.println("[config] To customize, create " + USER_CONFIG_PATH + " (see default-config.yaml for the schema).");
            return new LinkedHashMap<>();
        }
        try {
            String raw = Files.readString(USER_CONFIG_PATH);
            System.err.println("[config] Loaded user config from " + USER_CONFIG_PATH);
            return parseYaml(substituteEnvVars(raw));
        } catch (IOException e) {
            System.err.println("[config] WARNING: failed to read " + USER_CONFIG_PATH + ": " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Parse a YAML string into a {@code Map<String, Object>}.
     * Returns an empty map if the YAML is empty or null.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYaml(String yaml) {
        Yaml parser = new Yaml();
        Object result = parser.load(yaml);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------
    // Internal — env var substitution
    // ------------------------------------------------------------------

    /**
     * Replace all {@code ${ENV_VAR}} occurrences in the input string with the
     * value of the corresponding environment variable. If the variable is not
     * set, the placeholder is replaced with an empty string.
     */
    static String substituteEnvVars(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String envValue = System.getenv(envName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue != null ? envValue : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Internal — deep merge
    // ------------------------------------------------------------------

    /**
     * Recursively merge {@code overlay} into {@code base}. Values in
     * {@code overlay} take precedence. Both maps are treated as immutable;
     * a new map is returned.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object overlayValue = entry.getValue();
            Object baseValue = merged.get(key);
            if (baseValue instanceof Map && overlayValue instanceof Map) {
                merged.put(key, deepMerge(
                        (Map<String, Object>) baseValue,
                        (Map<String, Object>) overlayValue));
            } else {
                merged.put(key, overlayValue);
            }
        }
        return merged;
    }

    // ------------------------------------------------------------------
    // Internal — nested value navigation
    // ------------------------------------------------------------------

    /**
     * Navigate the nested map using dot-separated keys.
     * For example, {@code "anthropic.api-key"} first looks up the {@code "anthropic"}
     * sub-map, then the {@code "api-key"} entry within it.
     *
     * @return the value, or {@code null} if any segment is missing
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        Object current = config;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private String getStringValue(String key, String defaultValue) {
        Object value = getNestedValue(key);
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString();
        return str.isEmpty() ? defaultValue : str;
    }

    private int getIntValue(String key, int defaultValue) {
        Object value = getNestedValue(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanValue(String key, boolean defaultValue) {
        Object value = getNestedValue(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate() {
        Provider provider;
        try {
            provider = getProvider();
        } catch (IllegalArgumentException e) {
            System.err.println();
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("Set 'provider: anthropic' or 'provider: ollama' in your config.");
            System.err.println();
            System.exit(1);
            return;
        }

        // Anthropic provider requires an API key; Ollama is keyless.
        if (provider == Provider.ANTHROPIC) {
            String apiKey = getAnthropicApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                System.err.println();
                System.err.println("ERROR: Anthropic API key is not configured.");
                System.err.println();
                System.err.println("Please set it via one of the following methods:");
                System.err.println("  1. Environment variable:  export ANTHROPIC_API_KEY=sk-ant-...");
                System.err.println("  2. Config file:           " + USER_CONFIG_PATH);
                System.err.println("     Set 'anthropic.api-key' to your key value.");
                System.err.println();
                System.err.println("Or switch to a local provider with 'provider: ollama' in your config.");
                System.err.println();
                System.exit(1);
            }
        }
    }
}
