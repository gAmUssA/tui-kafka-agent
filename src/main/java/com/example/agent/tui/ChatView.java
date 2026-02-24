package com.example.agent.tui;

import java.util.List;

/**
 * Renders chat entries with badge-style role labels and styled tool result boxes.
 */
public final class ChatView {

  private ChatView() {
  }

  public static String render(List<ChatEntry> entries, int width) {
    var sb = new StringBuilder();
    for (ChatEntry entry : entries) {
      switch (entry.role()) {
        case USER -> {
          sb.append("  ").append(Theme.USER_BADGE.render("You"));
          sb.append("  ").append(Theme.USER.render(entry.content()));
          sb.append("\n\n");
        }
        case AGENT -> {
          sb.append("  ").append(Theme.AGENT_BADGE.render("Agent"));
          sb.append("  ").append(Theme.AGENT.render(entry.content()));
          sb.append("\n\n");
        }
        case TOOL -> {
          if (entry.toolName() != null) {
            sb.append(ToolResultView.render(entry.toolName(), entry.content(), width - 4));
          } else {
            sb.append("  ").append(Theme.TOOL_BADGE.render("System"));
            sb.append("  ").append(Theme.TOOL.render(entry.content()));
          }
          sb.append("\n\n");
        }
        case ERROR -> {
          sb.append("  ").append(Theme.ERROR_BADGE.render("Error"));
          sb.append("  ").append(Theme.ERROR.render(entry.content()));
          sb.append("\n\n");
        }
        case PRERENDERED -> {
          sb.append(entry.content());
          sb.append("\n\n");
        }
      }
    }
    return sb.toString();
  }
}
