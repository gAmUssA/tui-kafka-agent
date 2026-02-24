package com.example.agent.tui;

import java.util.List;

/**
 * Renders the command suggestion popup as styled text lines.
 * Displayed between the bottom separator and textarea when active.
 */
public final class CommandSuggestionView {

    private CommandSuggestionView() {
    }

    /**
     * Render the suggestion list as a compact box.
     *
     * @param suggestions  filtered command list
     * @param selectedIdx  index of the currently highlighted item
     * @param termWidth    terminal width for sizing
     * @return rendered string (no trailing newline)
     */
    public static String render(List<CommandRegistry.CommandInfo> suggestions, int selectedIdx, int termWidth) {
        if (suggestions.isEmpty()) {
            return "";
        }

        // Determine column widths
        int maxCmdLen = suggestions.stream()
            .mapToInt(c -> c.displayName().length())
            .max().orElse(10);
        maxCmdLen = Math.max(maxCmdLen, 10);

        var sb = new StringBuilder();
        String topBorder = Theme.SUGGESTION_BORDER.render("╭" + "─".repeat(Math.min(termWidth - 4, 70)) + "╮");
        String botBorder = Theme.SUGGESTION_BORDER.render("╰" + "─".repeat(Math.min(termWidth - 4, 70)) + "╯");

        sb.append(" ").append(topBorder).append("\n");

        for (int i = 0; i < suggestions.size(); i++) {
            var cmd = suggestions.get(i);
            String cmdName = cmd.displayName();
            String desc = cmd.description();

            // Pad command name for alignment
            String paddedCmd = String.format(" %-" + (maxCmdLen + 2) + "s", cmdName);
            String row;
            if (i == selectedIdx) {
                row = Theme.SUGGESTION_SELECTED.render(paddedCmd) + "  " + Theme.SUGGESTION_DIM.render(desc);
            } else {
                row = Theme.SUGGESTION_NORMAL.render(paddedCmd) + "  " + Theme.SUGGESTION_DIM.render(desc);
            }

            String pipe = Theme.SUGGESTION_BORDER.render("│");
            sb.append(" ").append(pipe).append(row);

            // Only add newline if not last row
            if (i < suggestions.size() - 1) {
                sb.append("\n");
            }
        }
        sb.append("\n");
        sb.append(" ").append(botBorder);

        return sb.toString();
    }
}
