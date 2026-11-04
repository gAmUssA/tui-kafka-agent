package com.example.agent.agentic;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Which MCP tools each specialist may see. Names are the Confluent MCP
 * server's tool names (58 as of mcp-confluent 1.6.0); unknown names are simply
 * absent, so this also survives server upgrades.
 */
final class ToolScopes {

    private ToolScopes() {
    }

    private static final Set<String> CONFLUENT_TOOLS = Set.of(
            "list-topics", "create-topics", "delete-topics",
            "produce-message", "consume-messages", "get-partition-offsets",
            "list-consumer-groups", "describe-consumer-group", "get-consumer-group-lag",
            "search-topics-by-tag", "search-topics-by-name",
            "create-topic-tags", "delete-tag", "remove-tag-from-entity", "add-tags-to-topic", "list-tags",
            "alter-topic-config", "get-topic-config",
            "list-schemas", "delete-schema");
    // Deliberately excluded: list-environments / list-clusters / read-environment.
    // The MCP server is pre-configured with the demo cluster, and offering them made
    // the agent enumerate infrastructure repeatedly (6× list-clusters in one turn).

    /** Kafka, Schema Registry, tags. */
    static final Predicate<String> CONFLUENT = DestructiveGuard.guard(CONFLUENT_TOOLS::contains);

    private static final Set<String> FLINK_READ_TOOLS = Set.of(
            "list-flink-catalogs", "list-flink-databases", "list-flink-tables",
            "describe-flink-table", "get-flink-table-info", "list-flink-statements");

    private static final Set<String> FLINK_SUBMIT_TOOLS = Set.of(
            "create-flink-statement", "read-flink-statement",
            "get-flink-statement-exceptions", "check-flink-statement-health");

    private static final Set<String> FLINK_OPS_TOOLS = Set.of(
            "list-flink-statements", "read-flink-statement", "delete-flink-statements",
            "get-flink-statement-exceptions", "check-flink-statement-health",
            "detect-flink-statement-issues", "get-flink-statement-profile");

    /** Inspect tables and catalogs only — for the SQL author and critic. */
    static final Predicate<String> FLINK_READ = FLINK_READ_TOOLS::contains;

    /** Submit one approved statement and confirm it runs — for the submitter. */
    static final Predicate<String> FLINK_SUBMIT = FLINK_SUBMIT_TOOLS::contains;

    /** Manage existing statements — for the flinkOps specialist. */
    static final Predicate<String> FLINK_OPS = DestructiveGuard.guard(FLINK_OPS_TOOLS::contains);
}
