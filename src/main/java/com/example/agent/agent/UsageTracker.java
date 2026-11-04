package com.example.agent.agent;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Single-session token + cost accumulator wired into LangChain4j as a
 * {@link ChatModelListener}. One instance is shared across both Anthropic and
 * Ollama models so switching providers via {@code /model} preserves totals.
 * <p>
 * Thread-safe: counters use {@link AtomicLong} and {@link DoubleAdder};
 * {@link #snapshot()} is a lock-free read suitable for polling from the TUI's
 * {@code view()} render path.
 * <p>
 * <strong>Streaming note:</strong> {@link #onResponse(ChatModelResponseContext)}
 * fires once per chat round-trip with the full {@link TokenUsage} — not per
 * streamed chunk. Multi-turn tool loops issue multiple round-trips, so the
 * accumulator will increment more than once per user message when tools fire.
 */
public final class UsageTracker implements ChatModelListener {

    /** Immutable snapshot of running totals; safe to pass to renderers. */
    public record Snapshot(
            long inputTokens,
            long outputTokens,
            long totalTokens,
            long requests,
            long errors,
            double estimatedCostUsd,
            String lastModel,
            long lastLatencyMs) {
    }

    /**
     * Per-1M-token pricing in USD. Models not in this map contribute $0 to cost.
     * {@code cachedInputPerMillion} prices prompt-cache reads; {@code cacheWritePerMillion}
     * prices Anthropic cache writes (OpenAI-compatible providers do not report them).
     */
    record Pricing(double inputPerMillion, double cachedInputPerMillion,
                           double cacheWritePerMillion, double outputPerMillion) {
    }

    // Add models you actually use. Keys are LangChain4j-reported model names
    // (i.e. exactly what comes back in ChatResponseMetadata.modelName()).
    // Anthropic: cache reads 0.1x input, 5-minute cache writes 1.25x input.
    // Fireworks: standard serverless list prices, checked 2026-11-03 (docs.fireworks.ai/serverless/pricing).
    private static final Map<String, Pricing> PRICES = Map.of(
            "claude-sonnet-4-6", new Pricing(3.00, 0.30, 3.75, 15.00),
            "claude-opus-4-7",   new Pricing(15.00, 1.50, 18.75, 75.00),
            "claude-haiku-4-5",  new Pricing(1.00, 0.10, 1.25, 5.00),
            "accounts/fireworks/models/deepseek-v4p1-flash", new Pricing(0.30, 0.006, 0, 1.20),
            "accounts/fireworks/models/kimi-k3",             new Pricing(3.00, 0.30, 0, 15.00),
            "accounts/fireworks/models/ember-1",             new Pricing(3.00, 0.30, 0, 15.00),
            "accounts/fireworks/models/glm-5p3-flash",       new Pricing(0.15, 0.03, 0, 0.50)
            // Ollama models intentionally absent — local inference is free
    );

    /** Key under which the request start time (nanos) is stored on attributes. */
    private static final String START_NS_KEY = "kafkaagent.usage.startNs";

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final DoubleAdder costUsd = new DoubleAdder();
    private volatile String lastModel = "";
    private volatile long lastLatencyMs;

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put(START_NS_KEY, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        requests.incrementAndGet();

        Object start = ctx.attributes().get(START_NS_KEY);
        if (start instanceof Long startNs) {
            lastLatencyMs = (System.nanoTime() - startNs) / 1_000_000L;
        }

        ChatResponseMetadata md = ctx.chatResponse().metadata();
        if (md.modelName() != null) {
            lastModel = md.modelName();
        }

        TokenUsage usage = md.tokenUsage();
        if (usage != null) {
            long in = nz(usage.inputTokenCount());
            long out = nz(usage.outputTokenCount());
            long tot = nz(usage.totalTokenCount());
            inputTokens.addAndGet(in);
            outputTokens.addAndGet(out);
            totalTokens.addAndGet(tot > 0 ? tot : in + out);

            Pricing p = pricingFor(md.modelName());
            if (p != null) {
                costUsd.add(cost(p, usage));
            }
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        errors.incrementAndGet();
    }

    /** Lock-free snapshot of current totals. Safe to call from any thread. */
    public Snapshot snapshot() {
        return new Snapshot(
                inputTokens.get(),
                outputTokens.get(),
                totalTokens.get(),
                requests.get(),
                errors.get(),
                costUsd.sum(),
                lastModel,
                lastLatencyMs);
    }

    /** Reset all accumulators to zero. */
    public void reset() {
        inputTokens.set(0);
        outputTokens.set(0);
        totalTokens.set(0);
        requests.set(0);
        errors.set(0);
        costUsd.reset();
        lastLatencyMs = 0;
        lastModel = "";
    }

    /**
     * Price entry for a reported model name. Providers may report a dated id
     * ({@code claude-haiku-4-5-20251001}), so the longest table key that prefixes
     * the name wins. Returns {@code null} for unpriced (local) models.
     */
    static Pricing pricingFor(String modelName) {
        if (modelName == null) {
            return null;
        }
        Pricing exact = PRICES.get(modelName);
        if (exact != null) {
            return exact;
        }
        String best = null;
        for (String key : PRICES.keySet()) {
            if (modelName.startsWith(key) && (best == null || key.length() > best.length())) {
                best = key;
            }
        }
        return best == null ? null : PRICES.get(best);
    }

    /**
     * Cost of one response. Anthropic reports cache reads and writes on top of
     * {@code input_tokens}; OpenAI-compatible providers report cached tokens as a
     * subset of {@code prompt_tokens}.
     */
    static double cost(Pricing p, TokenUsage usage) {
        long in = nz(usage.inputTokenCount());
        long out = nz(usage.outputTokenCount());
        long cacheRead = 0;
        long cacheWrite = 0;
        long uncachedIn = in;
        if (usage instanceof AnthropicTokenUsage a) {
            cacheRead = nz(a.cacheReadInputTokens());
            cacheWrite = nz(a.cacheCreationInputTokens());
        } else if (usage instanceof OpenAiTokenUsage o && o.inputTokensDetails() != null) {
            cacheRead = Math.min(in, nz(o.inputTokensDetails().cachedTokens()));
            uncachedIn = in - cacheRead;
        }
        return (uncachedIn * p.inputPerMillion
                + cacheRead * p.cachedInputPerMillion
                + cacheWrite * p.cacheWritePerMillion
                + out * p.outputPerMillion) / 1_000_000.0;
    }

    private static long nz(Integer v) {
        return v == null ? 0L : v.longValue();
    }
}
