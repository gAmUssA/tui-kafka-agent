package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Borders;
import com.williamcallahan.tui4j.compat.lipgloss.Position;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

/**
 * Static lipgloss style constants for consistent TUI theming.
 * Uses hex colors for a modern, professional look.
 */
public final class Theme {

    private Theme() {
    }

    // ── Color palette ───────────────────────────────────────────────────

    private static final String BLUE     = "#61AFEF";
    private static final String GREEN    = "#98C379";
    private static final String GOLD     = "#E5C07B";
    private static final String RED      = "#E06C75";
    private static final String DIM_GRAY = "#5C6370";
    private static final String DARK_BG  = "#282C34";
    private static final String SEP_GRAY = "#3E4451";
    private static final String WHITE    = "#ABB2BF";
    private static final String BRIGHT   = "#FFFFFF";

    // ── Role text styles ────────────────────────────────────────────────

    public static final Style USER = Style.newStyle()
            .foreground(Color.color(BLUE)).bold(true);

    public static final Style AGENT = Style.newStyle()
            .foreground(Color.color(GREEN));

    public static final Style TOOL = Style.newStyle()
            .foreground(Color.color(GOLD));

    public static final Style ERROR = Style.newStyle()
            .foreground(Color.color(RED)).bold(true);

    public static final Style SYSTEM = Style.newStyle()
            .foreground(Color.color(DIM_GRAY));

    // ── Badge pill styles (background-colored, white text) ──────────────

    public static final Style USER_BADGE = Style.newStyle()
            .foreground(Color.color(BRIGHT))
            .background(Color.color(BLUE))
            .bold(true)
            .padding(0, 1);

    public static final Style AGENT_BADGE = Style.newStyle()
            .foreground(Color.color(BRIGHT))
            .background(Color.color(GREEN))
            .bold(true)
            .padding(0, 1);

    public static final Style TOOL_BADGE = Style.newStyle()
            .foreground(Color.color(BRIGHT))
            .background(Color.color(GOLD))
            .bold(true)
            .padding(0, 1);

    public static final Style ERROR_BADGE = Style.newStyle()
            .foreground(Color.color(BRIGHT))
            .background(Color.color(RED))
            .bold(true)
            .padding(0, 1);

    // ── Header bar styles ───────────────────────────────────────────────

    public static final Style HEADER = Style.newStyle()
            .foreground(Color.color(BRIGHT))
            .background(Color.color(BLUE))
            .bold(true)
            .padding(0, 1);

    public static final Style HEADER_DIM = Style.newStyle()
            .foreground(Color.color(WHITE))
            .background(Color.color(DARK_BG))
            .padding(0, 1);

    public static final Style HEADER_FILL = Style.newStyle()
            .background(Color.color(DARK_BG));

    public static final Style HEADER_STATUS = Style.newStyle()
            .foreground(Color.color(GREEN))
            .background(Color.color(DARK_BG))
            .padding(0, 1);

    // ── Role labels (kept for backward compat, now delegates to badges) ─

    public static final Style USER_LABEL = USER_BADGE;
    public static final Style AGENT_LABEL = AGENT_BADGE;
    public static final Style TOOL_LABEL = TOOL_BADGE;
    public static final Style ERROR_LABEL = ERROR_BADGE;

    // ── Box and panel styles ────────────────────────────────────────────

    public static final Style TOOL_BOX = Style.newStyle()
            .border(Borders.roundedBorder())
            .borderForeground(Color.color(GOLD))
            .padding(0, 1);

    public static final Style TOOL_BOX_HEADER = Style.newStyle()
            .foreground(Color.color(GOLD))
            .bold(true);

    public static final Style TOOL_COLLAPSED_ICON = Style.newStyle()
            .foreground(Color.color(GOLD));

    public static final Style TOOL_COLLAPSED_META = Style.newStyle()
            .foreground(Color.color(DIM_GRAY))
            .italic(true);

    public static final Style HELP_BOX = Style.newStyle()
            .border(Borders.roundedBorder())
            .borderForeground(Color.color(BLUE))
            .padding(1, 2);

    public static final Style WELCOME_BOX = Style.newStyle()
            .border(Borders.roundedBorder())
            .borderForeground(Color.color(BLUE))
            .padding(1, 2)
            .align(Position.Center);

    public static final Style WELCOME_TITLE = Style.newStyle()
            .foreground(Color.color(BLUE))
            .bold(true);

    public static final Style WELCOME_TEXT = Style.newStyle()
            .foreground(Color.color(DIM_GRAY));

    // ── Input prompt ────────────────────────────────────────────────────

    public static final Style INPUT_PROMPT = Style.newStyle()
            .foreground(Color.color(BLUE))
            .bold(true);

    // ── Status indicators (styles — render at use-site, not at class-load) ──

    public static final Style STATUS_ACTIVE_STYLE = Style.newStyle()
            .foreground(Color.color(GREEN));

    public static final Style STATUS_INACTIVE_STYLE = Style.newStyle()
            .foreground(Color.color(DIM_GRAY));

    public static String statusActive() {
        return STATUS_ACTIVE_STYLE.render("\u25CF");
    }

    public static String statusInactive() {
        return STATUS_INACTIVE_STYLE.render("\u25CB");
    }

    // ── Separator ───────────────────────────────────────────────────────

    public static final Style SEPARATOR = Style.newStyle()
            .foreground(Color.color(SEP_GRAY));

    // ── Spinner / tool execution text ───────────────────────────────────

    public static final Style SPINNER_TEXT = Style.newStyle()
            .foreground(Color.color(DIM_GRAY))
            .italic(true);

    // ── Streaming cursor ────────────────────────────────────────────────

    public static final Style CURSOR = Style.newStyle()
            .foreground(Color.color(GREEN));

    // ── Alert severity styles ─────────────────────────────────────────

    public static final Style SEVERITY_CRITICAL = Style.newStyle()
            .foreground(Color.color(RED)).bold(true);

    public static final Style SEVERITY_WARNING = Style.newStyle()
            .foreground(Color.color(GOLD)).bold(true);

    public static final Style SEVERITY_INFO = Style.newStyle()
            .foreground(Color.color(BLUE)).bold(true);

    // ── Tool result box border (kept for backward compat) ───────────────

    public static final Style TOOL_BOX_BORDER = Style.newStyle()
            .foreground(Color.color(GOLD));

    // ── Command suggestion popup styles ──────────────────────────────────

    public static final Style SUGGESTION_SELECTED = Style.newStyle()
            .foreground(Color.color(BRIGHT))
            .background(Color.color(SEP_GRAY))
            .bold(true);

    public static final Style SUGGESTION_NORMAL = Style.newStyle()
            .foreground(Color.color(WHITE));

    public static final Style SUGGESTION_DIM = Style.newStyle()
            .foreground(Color.color(DIM_GRAY));

    public static final Style SUGGESTION_BORDER = Style.newStyle()
            .foreground(Color.color(SEP_GRAY));
}
