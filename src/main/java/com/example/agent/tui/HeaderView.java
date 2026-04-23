package com.example.agent.tui;

import com.example.agent.agent.Provider;
import com.example.agent.agent.UsageTracker;

/**
 * Renders a professional status bar filling the full terminal width.
 * Tracks plain-text length separately from styled output to calculate
 * exact padding, avoiding ANSI-nesting width miscalculations.
 */
public final class HeaderView {

  private HeaderView() {
  }

  public static String render(String appNameText, Provider provider, String model,
                              int toolCount, int serverCount,
                              boolean isStreaming, boolean isToolExecuting, int width) {
    var styled = new StringBuilder();
    int visibleLen = 0;

    // App name — HEADER style has padding(0,1) = +2 visible chars
    styled.append(Theme.HEADER.render(appNameText));
    visibleLen += appNameText.length() + 2;

    // Separator + provider:model — HEADER_DIM has padding(0,1) = +2 visible chars
    String providerModel = (provider != null ? provider.toString() : "?") + ":" + shortenModel(model);
    String sepModel = " │ " + providerModel;
    styled.append(Theme.HEADER_DIM.render(sepModel));
    visibleLen += sepModel.length() + 2;

    // Tool count — HEADER_DIM has padding(0,1) = +2 visible chars
    if (toolCount > 0) {
      String toolText = " │ MCP: " + toolCount + " tools"
              + (serverCount > 1 ? " (" + serverCount + " servers)" : "");
      styled.append(Theme.HEADER_DIM.render(toolText));
      visibleLen += toolText.length() + 2;
    }

    // Status indicator
    if (isToolExecuting) {
      String sep = " │ ";
      styled.append(Theme.HEADER_DIM.render(sep));
      visibleLen += sep.length() + 2; // HEADER_DIM padding

      // statusActive() renders a dot; HEADER_STATUS has padding(0,1)
      String statusContent = Theme.statusActive() + " calling tool...";
      styled.append(Theme.HEADER_STATUS.render(statusContent));
      visibleLen += 1 + " calling tool...".length() + 2; // dot + text + padding
    } else if (isStreaming) {
      String sep = " │ ";
      styled.append(Theme.HEADER_DIM.render(sep));
      visibleLen += sep.length() + 2;

      String statusContent = Theme.statusActive() + " streaming...";
      styled.append(Theme.HEADER_STATUS.render(statusContent));
      visibleLen += 1 + " streaming...".length() + 2;
    }

    // Pad remaining width with background-colored spaces
    int padding = Math.max(0, width - visibleLen);
    if (padding > 0) {
      styled.append(Theme.HEADER_FILL.render(" ".repeat(padding)));
    }

    return styled.toString();
  }

  /**
   * Render a one-line usage summary below the main header bar.
   * Always returns a fixed-height line (even before the first request) so
   * the chrome height stays stable across renders.
   */
  public static String renderUsage(UsageTracker.Snapshot s) {
    if (s.requests() == 0) {
      return Theme.SYSTEM.render("  tokens: — (no requests yet)");
    }
    String costPart = s.estimatedCostUsd() > 0
            ? String.format("  $%.4f", s.estimatedCostUsd())
            : "  $0.00 (local)";
    String errPart = s.errors() > 0 ? "  " + s.errors() + " err" : "";
    String text = String.format(
            "  tokens: %s in · %s out%s  last %dms  %d req%s%s",
            formatN(s.inputTokens()),
            formatN(s.outputTokens()),
            costPart,
            s.lastLatencyMs(),
            s.requests(),
            s.requests() == 1 ? "" : "s",
            errPart);
    return Theme.SYSTEM.render(text);
  }

  private static String formatN(long n) {
    return String.format("%,d", n);
  }

  private static String shortenModel(String model) {
      if (model == null) {
          return "unknown";
      }
      if (model.contains("sonnet")) {
          return "sonnet";
      }
      if (model.contains("haiku")) {
          return "haiku";
      }
      if (model.contains("opus")) {
          return "opus";
      }
    int lastDash = model.lastIndexOf('-');
      if (lastDash > 20) {
          return model.substring(0, lastDash);
      }
    return model;
  }
}
