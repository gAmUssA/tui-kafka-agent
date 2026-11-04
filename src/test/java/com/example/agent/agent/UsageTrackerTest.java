package com.example.agent.agent;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageTrackerTest {

    private static final UsageTracker.Pricing DEEPSEEK = new UsageTracker.Pricing(0.30, 0.006, 0, 1.20);
    private static final UsageTracker.Pricing SONNET = new UsageTracker.Pricing(3.00, 0.30, 3.75, 15.00);

    @Test
    void openAiCompatibleCachedTokensAreBilledAtTheCachedRate() {
        TokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(1_000_000)
                .outputTokenCount(0)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(900_000).build())
                .build();
        // 100k uncached at $0.30/M + 900k cached at $0.006/M
        assertEquals(0.0354, UsageTracker.cost(DEEPSEEK, usage), 1e-9);
    }

    @Test
    void datedAnthropicModelIdFindsItsPrice() {
        assertEquals(new UsageTracker.Pricing(1.00, 0.10, 1.25, 5.00),
                UsageTracker.pricingFor("claude-haiku-4-5-20251001"));
    }

    @Test
    void unknownLocalModelHasNoPrice() {
        assertEquals(null, UsageTracker.pricingFor("qwen2.5:7b"));
    }

    @Test
    void usageWithoutCacheDetailsIsBilledAtListPrice() {
        assertEquals(0.30 + 1.20, UsageTracker.cost(DEEPSEEK, new TokenUsage(1_000_000, 1_000_000)), 1e-9);
    }

    @Test
    void anthropicCacheReadsAndWritesAddToUncachedInput() {
        TokenUsage usage = AnthropicTokenUsage.builder()
                .inputTokenCount(100_000)
                .outputTokenCount(10_000)
                .cacheReadInputTokens(800_000)
                .cacheCreationInputTokens(100_000)
                .build();
        // 100k x $3 + 800k x $0.30 + 100k x $3.75 + 10k x $15, per million
        assertEquals(0.30 + 0.24 + 0.375 + 0.15, UsageTracker.cost(SONNET, usage), 1e-9);
    }
}
