package com.example.agent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelSwitcherTest {

    @Test
    void openWeightAliasSwitchesProviderAndModelFromAnthropic() {
        var r = ModelSwitcher.resolve("kimi", Provider.ANTHROPIC);
        assertEquals(Provider.FIREWORKS, r.provider());
        assertEquals("accounts/fireworks/models/kimi-k3", r.modelName());
    }

    @Test
    void claudeAliasSwitchesBackFromFireworks() {
        var r = ModelSwitcher.resolve("sonnet", Provider.FIREWORKS);
        assertEquals(Provider.ANTHROPIC, r.provider());
        assertEquals("claude-sonnet-4-6", r.modelName());
    }

    @Test
    void providerPrefixResolvesItsOwnAlias() {
        var r = ModelSwitcher.resolve("fireworks:deepseek", Provider.ANTHROPIC);
        assertEquals(Provider.FIREWORKS, r.provider());
        assertEquals("accounts/fireworks/models/deepseek-v4p1-flash", r.modelName());
    }

    @Test
    void providerPrefixPassesLiteralModelIdThrough() {
        var r = ModelSwitcher.resolve("fireworks:accounts/fireworks/models/gpt-oss-120b", Provider.ANTHROPIC);
        assertEquals(Provider.FIREWORKS, r.provider());
        assertEquals("accounts/fireworks/models/gpt-oss-120b", r.modelName());
    }

    @Test
    void ollamaTagColonIsNotMistakenForAProvider() {
        var r = ModelSwitcher.resolve("qwen2.5:7b", Provider.OLLAMA);
        assertEquals(Provider.OLLAMA, r.provider());
        assertEquals("qwen2.5:7b", r.modelName());
    }

    @Test
    void unknownNameKeepsCurrentProvider() {
        var r = ModelSwitcher.resolve("claude-opus-4-7", Provider.ANTHROPIC);
        assertEquals(Provider.ANTHROPIC, r.provider());
        assertEquals("claude-opus-4-7", r.modelName());
    }

    @Test
    void blankInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ModelSwitcher.resolve("  ", Provider.ANTHROPIC));
    }

    @Test
    void shortNameKeepsLastPathSegment() {
        assertEquals("kimi-k3", ModelSwitcher.shortName("accounts/fireworks/models/kimi-k3"));
        assertEquals("sonnet", ModelSwitcher.shortName("claude-sonnet-4-6"));
    }
}
