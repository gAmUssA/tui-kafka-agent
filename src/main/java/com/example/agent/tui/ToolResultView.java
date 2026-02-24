package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;

/**
 * Renders a tool result as a lipgloss-bordered box with a styled tool name header.
 */
public final class ToolResultView {

    private ToolResultView() {
    }

    public static String render(String toolName, String result, int width) {
        int boxWidth = Math.max(20, Math.min(width, 120));

        // Build content: header line + severity-styled result
        String header = Theme.TOOL_BOX_HEADER.render(toolName + "()");
        String content = header + "\n" + styleSeverity(result);

        // Apply the themed box style with constrained width
        Style box = Theme.TOOL_BOX.width(boxWidth);
        return "  " + box.render(content);
    }

    public static String renderCollapsed(String toolName, String result, int width) {
        int lineCount = result.split("\n", -1).length;
        String summary = toolName + "()";
        String meta = lineCount + " line" + (lineCount != 1 ? "s" : "");
        return "  " + Theme.TOOL_COLLAPSED_ICON.render("\u25B6 ")
             + Theme.TOOL_BOX_HEADER.render(summary)
             + Theme.TOOL_COLLAPSED_META.render(" \u2014 " + meta);
    }

    /**
     * Highlights severity keywords (CRITICAL, WARNING, INFO) with appropriate colors.
     */
    static String styleSeverity(String text) {
        text = text.replace("CRITICAL", Theme.SEVERITY_CRITICAL.render("CRITICAL"));
        text = text.replace("WARNING", Theme.SEVERITY_WARNING.render("WARNING"));
        text = text.replace("INFO", Theme.SEVERITY_INFO.render("INFO"));
        return text;
    }
}
