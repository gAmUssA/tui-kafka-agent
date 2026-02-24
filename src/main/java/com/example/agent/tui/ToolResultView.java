package com.example.agent.tui;

/**
 * Renders a tool result as a bordered box with tool name header.
 */
public final class ToolResultView {

    private ToolResultView() {
    }

    public static String render(String toolName, String result, int width) {
        int innerWidth = Math.max(10, width - 4);
        String header = " " + toolName + "() ";
        String topBorder = "┌" + header + "─".repeat(Math.max(0, innerWidth - header.length())) + "┐";
        String bottomBorder = "└" + "─".repeat(innerWidth) + "┘";

        var sb = new StringBuilder();
        sb.append(topBorder).append("\n");

        String[] lines = result.split("\n", -1);
        for (String line : lines) {
            if (line.length() > innerWidth) {
                line = line.substring(0, innerWidth - 1) + "…";
            }
            sb.append("│ ").append(line);
            int padding = innerWidth - line.length() - 1;
            if (padding > 0) {
                sb.append(" ".repeat(padding));
            }
            sb.append("│\n");
        }

        sb.append(bottomBorder);
        return sb.toString();
    }
}
