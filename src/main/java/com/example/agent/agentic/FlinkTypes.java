package com.example.agent.agentic;

import dev.langchain4j.model.output.structured.Description;

/**
 * The typed objects that flow between the Flink specialists. Routing in the
 * verify loop branches on {@link CriticResult#successful()} — a real field,
 * not a parsed score — and the submitter receives a fully typed statement.
 */
public final class FlinkTypes {

    private FlinkTypes() {
    }

    /** A Flink SQL statement proposed by the author. */
    public record FlinkStatement(
            @Description("Short kebab-case statement name, e.g. brewery-temp-alerts") String name,
            @Description("The complete Flink SQL text, ready to submit, no markdown fences") String sql,
            @Description("Source table(s) read by the statement") String sourceTables,
            @Description("Sink table written by the statement, or empty for a SELECT") String sinkTable,
            @Description("What DESCRIBE showed and why the SQL is shaped this way, 1-3 sentences") String rationale) {

        public static FlinkStatement none() {
            return new FlinkStatement("", "", "", "", "");
        }

        public boolean isEmpty() {
            return sql == null || sql.isBlank();
        }
    }

    /** The critic's verdict on a {@link FlinkStatement}. */
    public record CriticResult(
            @Description("true only if the statement is correct and safe to submit as-is") boolean successful,
            @Description("If not successful: concrete, actionable problems and how to fix them. Empty when successful.") String feedback,
            @Description("The statement that was reviewed") FlinkStatement statement) {

        public static CriticResult pending() {
            return new CriticResult(false, "(first attempt — nothing reviewed yet)", FlinkStatement.none());
        }

        /** One-line summary for the TUI trace. */
        public String summary() {
            if (successful) {
                return "PASS";
            }
            String f = (feedback == null) ? "" : feedback.replace('\n', ' ').trim();
            return "FAIL — " + (f.length() > 90 ? f.substring(0, 90) + "…" : f);
        }
    }
}
