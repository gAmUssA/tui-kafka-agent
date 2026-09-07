package com.example.agent.agentic;

import com.example.agent.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads per-agent prompts: a config override ({@code agentic.prompts.<name>})
 * wins, otherwise the bundled {@code prompts/<name>.txt} resource.
 */
final class Prompts {

    private Prompts() {
    }

    static String load(AppConfig config, String name) {
        String override = config.getAgenticPromptOverride(name);
        if (override != null) {
            return override;
        }
        String resource = "prompts/" + name + ".txt";
        try (InputStream in = Prompts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled prompt: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled prompt: " + resource, e);
        }
    }
}
