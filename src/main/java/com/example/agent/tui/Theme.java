package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Borders;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

/**
 * Static lipgloss style constants for consistent TUI theming.
 */
public final class Theme {

    private Theme() {
    }

    // Role colors
    public static final Style USER = Style.newStyle()
            .foreground(Color.color("6")).bold(true);   // cyan

    public static final Style AGENT = Style.newStyle()
            .foreground(Color.color("2"));              // green

    public static final Style TOOL = Style.newStyle()
            .foreground(Color.color("3"));              // yellow

    public static final Style ERROR = Style.newStyle()
            .foreground(Color.color("1")).bold(true);   // red

    public static final Style SYSTEM = Style.newStyle()
            .foreground(Color.color("8"));              // dim gray

    // Role labels
    public static final Style USER_LABEL = Style.newStyle()
            .foreground(Color.color("6")).bold(true);

    public static final Style AGENT_LABEL = Style.newStyle()
            .foreground(Color.color("2")).bold(true);

    public static final Style TOOL_LABEL = Style.newStyle()
            .foreground(Color.color("3")).bold(true);

    public static final Style ERROR_LABEL = Style.newStyle()
            .foreground(Color.color("1")).bold(true);

    // Header bar
    public static final Style HEADER = Style.newStyle()
            .foreground(Color.color("15"))
            .background(Color.color("4"))
            .bold(true)
            .padding(0, 1);

    public static final Style HEADER_DIM = Style.newStyle()
            .foreground(Color.color("7"))
            .background(Color.color("4"))
            .padding(0, 1);

    // Tool result box border
    public static final Style TOOL_BOX_BORDER = Style.newStyle()
            .foreground(Color.color("3"));              // yellow border

    // Separator
    public static final Style SEPARATOR = Style.newStyle()
            .foreground(Color.color("8"));              // dim gray
}
