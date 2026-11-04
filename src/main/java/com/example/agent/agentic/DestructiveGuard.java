package com.example.agent.agentic;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Offers delete tools to the agents only during a turn whose user message explicitly
 * asks for removal. A mis-routed or over-eager agent cannot call a tool it is not given:
 * at rehearsal, "Did the alerts fire?" deleted the running alert statement.
 * <p>
 * {@link AgenticBridge} sets the flag at the start of each turn; the tool filters in
 * {@link ToolScopes} read it every time a tool list is built.
 */
final class DestructiveGuard {

    /** Tools that delete state on Confluent Cloud. */
    static final Set<String> DESTRUCTIVE_TOOLS = Set.of(
            "delete-flink-statements", "delete-topics", "delete-schema", "delete-tag",
            "remove-tag-from-entity", "delete-connector", "delete-tableflow-topic",
            "delete-tableflow-catalog-integration");

    private static final Pattern REMOVAL_INTENT = Pattern.compile(
            "\\b(delete|remove|reset|drop|clean\\s*up|tear\\s*down|purge)\\b", Pattern.CASE_INSENSITIVE);

    private static volatile boolean allowed;

    private DestructiveGuard() {
    }

    /** Called once per turn with the user's message. */
    static void beginTurn(String userInput) {
        allowed = userInput != null && REMOVAL_INTENT.matcher(userInput).find();
    }

    static boolean allowed() {
        return allowed;
    }

    /** {@code scope} narrowed so delete tools pass only while the current turn allows them. */
    static java.util.function.Predicate<String> guard(java.util.function.Predicate<String> scope) {
        return name -> scope.test(name) && (!DESTRUCTIVE_TOOLS.contains(name) || allowed);
    }
}
