package com.example.agent.agentic;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * Answers a call to a tool that does not exist instead of aborting the turn.
 * <p>
 * LangChain4j's default throws, which kills the whole agentic turn. Smaller models
 * often guess the snake_case spelling ({@code describe_flink_table}) of this MCP
 * server's kebab-case tools ({@code describe-flink-table}) — Confluent's managed MCP
 * server uses snake_case. Telling the model the right name lets it retry in-loop.
 */
final class ToolNameRecovery {

    private ToolNameRecovery() {
    }

    static ToolExecutionResultMessage reply(ToolExecutionRequest request) {
        return ToolExecutionResultMessage.from(request, message(request.name()));
    }

    static String message(String name) {
        String text = "Error: there is no tool named '" + name + "'. Call only tools from your tool list.";
        if (name != null && name.contains("_")) {
            text += " Tool names here use hyphens, not underscores: try '" + name.replace('_', '-') + "'.";
        }
        return text;
    }
}
