package com.example.agent.tui;

import java.util.List;

/**
 * Renders chat entries with role-specific colors and tool result borders.
 */
public final class ChatView {

    private ChatView() {
    }

    public static String render(List<ChatEntry> entries, int width) {
        var sb = new StringBuilder();
        for (ChatEntry entry : entries) {
            switch (entry.role()) {
                case USER -> {
                    sb.append("  ").append(Theme.USER_LABEL.render("You: "));
                    sb.append(Theme.USER.render(entry.content()));
                    sb.append("\n\n");
                }
                case AGENT -> {
                    sb.append("  ").append(Theme.AGENT_LABEL.render("Agent: "));
                    sb.append(Theme.AGENT.render(entry.content()));
                    sb.append("\n\n");
                }
                case TOOL -> {
                    if (entry.toolName() != null) {
                        sb.append(Theme.TOOL.render(
                                ToolResultView.render(entry.toolName(), entry.content(), width - 4)));
                    } else {
                        sb.append("  ").append(Theme.TOOL.render(entry.content()));
                    }
                    sb.append("\n\n");
                }
                case ERROR -> {
                    sb.append("  ").append(Theme.ERROR_LABEL.render("Error: "));
                    sb.append(Theme.ERROR.render(entry.content()));
                    sb.append("\n\n");
                }
            }
        }
        return sb.toString();
    }
}
