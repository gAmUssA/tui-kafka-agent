package com.example.agent.agent;

import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Queries the local Ollama server's {@code /api/tags} endpoint to list
 * locally-installed models. Used to validate {@code /model ollama:&lt;name&gt;}
 * input and to power model-name autocompletion.
 * <p>
 * The result is cached for {@link #CACHE_TTL} after a successful query;
 * call {@link #refresh(String)} explicitly to invalidate.
 * <p>
 * JSON parsing reuses snakeyaml (already on the classpath) since JSON is a
 * subset of YAML — keeps the dependency footprint identical.
 */
public final class OllamaModelDiscovery {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private List<String> cached = List.of();
    private long cachedAtMillis = 0L;
    private String cachedFor = "";

    /**
     * Return the list of locally-installed Ollama model names (e.g.
     * {@code "qwen2.5:7b"}, {@code "llama3.1:8b"}). Uses cache when fresh.
     * Returns an empty list if Ollama is unreachable.
     */
    public List<String> list(String baseUrl) {
        long now = System.currentTimeMillis();
        if (baseUrl.equals(cachedFor) && (now - cachedAtMillis) < CACHE_TTL.toMillis()) {
            return cached;
        }
        return refresh(baseUrl);
    }

    /**
     * Force a fresh query of the Ollama server, bypassing cache.
     * Returns an empty list if the server is unreachable or returns malformed data.
     */
    public synchronized List<String> refresh(String baseUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlash(baseUrl) + "/api/tags"))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return Collections.emptyList();
            }
            List<String> names = parseModelNames(resp.body());
            cached = names;
            cachedAtMillis = System.currentTimeMillis();
            cachedFor = baseUrl;
            return names;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Check whether {@code modelName} appears in the locally-installed list.
     * Useful for validating user input before issuing a chat request that
     * would otherwise fail with an opaque "model not found" from Ollama.
     */
    public boolean isInstalled(String baseUrl, String modelName) {
        return list(baseUrl).contains(modelName);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseModelNames(String json) {
        // Ollama /api/tags response shape:
        // { "models": [ {"name": "qwen2.5:7b", "size": ..., ...}, ... ] }
        Object parsed = new Yaml().load(json);
        if (!(parsed instanceof Map<?, ?> root)) {
            return Collections.emptyList();
        }
        Object modelsObj = root.get("models");
        if (!(modelsObj instanceof List<?> models)) {
            return Collections.emptyList();
        }
        return models.stream()
                .filter(m -> m instanceof Map)
                .map(m -> ((Map<String, Object>) m).get("name"))
                .filter(n -> n != null)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
