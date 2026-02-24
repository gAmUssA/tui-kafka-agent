package com.example.agent.tui;

/**
 * Renders the header bar showing app name, model, MCP status, and state.
 */
public final class HeaderView {

    private HeaderView() {
    }

    public static String render(String model, int toolCount, boolean isStreaming,
                                boolean isToolExecuting, int width) {
        var sb = new StringBuilder();
        sb.append(Theme.HEADER.render(" kafka-agent "));
        sb.append(Theme.HEADER_DIM.render(" " + shortenModel(model) + " "));

        if (toolCount > 0) {
            sb.append(Theme.HEADER_DIM.render(" MCP: " + toolCount + " tools "));
        }

        if (isToolExecuting) {
            sb.append(Theme.HEADER_DIM.render(" calling tool... "));
        } else if (isStreaming) {
            sb.append(Theme.HEADER_DIM.render(" streaming... "));
        }

        return sb.toString();
    }

    private static String shortenModel(String model) {
        if (model == null) return "unknown";
        if (model.contains("sonnet")) return "sonnet";
        if (model.contains("haiku")) return "haiku";
        if (model.contains("opus")) return "opus";
        // Return last segment if it's a long model ID
        int lastDash = model.lastIndexOf('-');
        if (lastDash > 20) return model.substring(0, lastDash);
        return model;
    }
}
