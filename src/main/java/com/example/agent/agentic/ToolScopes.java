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
    static final Predicate<String> CONFLUENT = CONFLUENT_TOOLS::contains;

    /** Everything Flink: statements, tables, catalogs, health, exceptions. */
    static final Predicate<String> FLINK = name -> name.contains("flink");
}
