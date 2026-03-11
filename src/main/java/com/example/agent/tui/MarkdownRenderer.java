package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.lipgloss.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight markdown-to-styled-terminal renderer using lipgloss.
 * Handles headers, bold, italic, inline code, fenced code blocks,
 * lists, horizontal rules, and blockquotes.
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    // Inline patterns (order matters — bold before italic)
    private static final Pattern BOLD_ITALIC = Pattern.compile("\\*\\*\\*(.+?)\\*\\*\\*");
    private static final Pattern BOLD        = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC      = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK        = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");

    /**
     * Render markdown content to styled terminal text.
     * Each output line is independently styled (no ANSI spanning newlines).
     */
    public static String render(String markdown, int width) {
        if (markdown == null || markdown.isEmpty()) return "";

        String[] lines = markdown.split("\n", -1);
        var result = new ArrayList<String>();
        boolean inCodeBlock = false;
        String codeLang = "";
        var codeBuffer = new ArrayList<String>();

        for (String line : lines) {
            // ── Fenced code blocks ────────────────────────────────
            if (line.trim().startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLang = line.trim().substring(3).trim();
                    codeBuffer.clear();
                } else {
                    // End of code block — render buffered code
                    result.add(renderCodeBlock(codeBuffer, codeLang, width));
                    inCodeBlock = false;
                    codeLang = "";
                }
                continue;
            }

            if (inCodeBlock) {
                codeBuffer.add(line);
                continue;
            }

            // ── Horizontal rule ───────────────────────────────────
            if (line.matches("^\\s*[-*_]{3,}\\s*$")) {
                int ruleWidth = Math.max(1, Math.min(width - 8, 60));
                result.add(Theme.MD_HR.render("─".repeat(ruleWidth)));
                continue;
            }

            // ── Headers ───────────────────────────────────────────
            if (line.startsWith("### ")) {
                String text = line.substring(4).trim();
                result.add(Theme.MD_H3.render(text));
                continue;
            }
            if (line.startsWith("## ")) {
                String text = line.substring(3).trim();
                result.add(Theme.MD_H2.render(text));
                continue;
            }
            if (line.startsWith("# ")) {
                String text = line.substring(2).trim();
                result.add(Theme.MD_H1.render(text));
                continue;
            }

            // ── Blockquote ────────────────────────────────────────
            if (line.startsWith("> ")) {
                String text = line.substring(2);
                result.add(Theme.MD_BLOCKQUOTE_BAR.render("│") + " " + Theme.MD_BLOCKQUOTE.render(renderInline(text)));
                continue;
            }
            if (line.equals(">")) {
                result.add(Theme.MD_BLOCKQUOTE_BAR.render("│"));
                continue;
            }

            // ── Unordered list ────────────────────────────────────
            var ulMatch = line.matches("^(\\s*)[*\\-+]\\s+(.*)$");
            if (ulMatch) {
                int indent = line.indexOf(line.trim().charAt(0));
                String text = line.trim().substring(2).trim();
                String bullet = Theme.MD_LIST_BULLET.render("•");
                result.add(" ".repeat(indent) + bullet + " " + renderInline(text));
                continue;
            }

            // ── Ordered list ──────────────────────────────────────
            if (line.matches("^\\s*\\d+\\.\\s+.*$")) {
                var m = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)$").matcher(line);
                if (m.matches()) {
                    String indent = m.group(1);
                    String num = m.group(2);
                    String text = m.group(3);
                    String numStyled = Theme.MD_LIST_NUM.render(num + ".");
                    result.add(indent + numStyled + " " + renderInline(text));
                    continue;
                }
            }

            // ── Regular paragraph text ────────────────────────────
            if (line.isBlank()) {
                result.add("");
            } else {
                result.add(renderInline(line));
            }
        }

        // Handle unclosed code block
        if (inCodeBlock && !codeBuffer.isEmpty()) {
            result.add(renderCodeBlock(codeBuffer, codeLang, width));
        }

        return String.join("\n", result);
    }

    /**
     * Render inline markdown: bold, italic, code, links.
     * Returns a string with ANSI styling applied.
     */
    static String renderInline(String text) {
        // Process inline code FIRST (protect code from bold/italic processing)
        var codeSegments = new ArrayList<String>();
        text = extractAndReplace(text, INLINE_CODE, codeSegments, "\u0000IC");

        // Bold+italic (***text***)
        text = replaceAll(text, BOLD_ITALIC, m ->
                Theme.MD_BOLD_ITALIC.render(m.group(1)));

        // Bold (**text**)
        text = replaceAll(text, BOLD, m ->
                Theme.MD_BOLD.render(m.group(1)));

        // Italic (*text*)
        text = replaceAll(text, ITALIC, m ->
                Theme.MD_ITALIC.render(m.group(1)));

        // Links [text](url) → text (url)
        text = replaceAll(text, LINK, m ->
                Theme.MD_LINK_TEXT.render(m.group(1))
                + Theme.MD_LINK_URL.render(" (" + m.group(2) + ")"));

        // Restore inline code with styling
        for (int i = codeSegments.size() - 1; i >= 0; i--) {
            text = text.replace("\u0000IC" + i, Theme.MD_INLINE_CODE.render(codeSegments.get(i)));
        }

        return text;
    }

    /**
     * Render a fenced code block with language label and styled border.
     */
    private static String renderCodeBlock(List<String> lines, String lang, int width) {
        String code = String.join("\n", lines);
        int boxWidth = Math.max(20, Math.min(width - 6, 100));

        String header = "";
        if (!lang.isEmpty()) {
            header = Theme.MD_CODE_LANG.render(lang) + "\n";
        }

        return header + Theme.MD_CODE_BLOCK.width(boxWidth).render(code);
    }

    // ── Utility helpers ───────────────────────────────────────────────

    /**
     * Extract pattern matches, replace with placeholders, and store originals.
     */
    private static String extractAndReplace(String text, Pattern pattern,
                                             List<String> store, String prefix) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int idx = store.size();
            store.add(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(prefix + idx));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replace all matches using a function.
     */
    private static String replaceAll(String text, Pattern pattern,
                                      java.util.function.Function<Matcher, String> replacer) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
