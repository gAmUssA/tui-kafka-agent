package com.example.agent.tui2;

/**
 * One row of the Activity pane: an agent hand-off or a tool call. The row is a
 * one-liner; {@link #detail} is what the detail overlay shows on Enter.
 */
final class Activity {

    enum Kind { AGENT_START, AGENT_DONE, TOOL_RUNNING, TOOL_DONE, ERROR, NOTE }

    Kind kind;
    final String agent;
    final String title;
    String detail;
    long durationMs;
    String summary;

    Activity(Kind kind, String agent, String title, String detail) {
        this.kind = kind;
        this.agent = agent;
        this.title = title;
        this.detail = detail;
    }

    static String duration(long ms) {
        return ms < 1000 ? ms + " ms" : String.format("%.1f s", ms / 1000.0);
    }

    static int lineCount(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        return s.split("\n", -1).length;
    }

    /** The one-line label rendered in the pane. */
    String label() {
        return switch (kind) {
            case AGENT_START -> "▸ " + agent + " working…";
            case AGENT_DONE -> "✔ " + agent + "  " + duration(durationMs)
                    + (summary == null || summary.isBlank() ? "" : "  → " + summary);
            case TOOL_RUNNING -> "   ⚙ " + title + " …";
            case TOOL_DONE -> "   ⚙ " + title + "  (" + lineCount(detail) + " lines)";
            case ERROR -> "✖ " + (agent == null ? "" : agent + ": ") + title;
            case NOTE -> "· " + title;
        };
    }
}
