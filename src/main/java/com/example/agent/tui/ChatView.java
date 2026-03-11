package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;

import java.util.List;

/**
 * Renders chat entries with badge-style role labels and styled tool result boxes.
 */
public final class ChatView {

  private ChatView() {
  }

  public static String render(List<ChatEntry> entries, int width, boolean toolOutputExpanded) {
    var sb = new StringBuilder();
    for (ChatEntry entry : entries) {
      switch (entry.role()) {
        case USER -> {
          sb.append("  ").append(Theme.USER_BADGE.render("You")).append("\n");
          appendStyledLines(sb, entry.content(), Theme.USER);
          sb.append("\n");
        }
        case AGENT -> {
          sb.append("  ").append(Theme.AGENT_BADGE.render("Agent")).append("\n");
          String rendered = MarkdownRenderer.render(entry.content(), width - 4);
          appendRenderedLines(sb, rendered);
          sb.append("\n");
        }
        case TOOL -> {
          if (entry.toolName() != null) {
            if (toolOutputExpanded) {
              sb.append(ToolResultView.render(entry.toolName(), entry.content(), width - 4));
            } else {
              sb.append(ToolResultView.renderCollapsed(entry.toolName(), entry.content(), width - 4));
            }
          } else {
            sb.append("  ").append(Theme.TOOL_BADGE.render("System")).append("\n");
            appendStyledLines(sb, entry.content(), Theme.TOOL);
          }
          sb.append("\n\n");
        }
        case ERROR -> {
          sb.append("  ").append(Theme.ERROR_BADGE.render("Error")).append("\n");
          appendStyledLines(sb, entry.content(), Theme.ERROR);
          sb.append("\n");
        }
        case PRERENDERED -> {
          sb.append(entry.content());
          sb.append("\n\n");
        }
      }
    }
    return sb.toString();
  }

  /**
   * Renders multi-line content line-by-line with consistent indent and styling.
   * Each line gets its own Style.render() call so ANSI codes don't span newlines.
   */
  private static void appendStyledLines(StringBuilder sb, String content, Style style) {
    String[] lines = content.split("\n", -1);
    for (String line : lines) {
      sb.append("  ").append(style.render(line)).append("\n");
    }
  }

  /**
   * Appends already-rendered (markdown-styled) lines with consistent indent.
   */
  private static void appendRenderedLines(StringBuilder sb, String rendered) {
    String[] lines = rendered.split("\n", -1);
    for (String line : lines) {
      sb.append("  ").append(line).append("\n");
    }
  }
}
