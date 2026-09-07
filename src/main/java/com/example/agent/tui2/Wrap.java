package com.example.agent.tui2;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy word wrap for list rows. TamboUI list cells do not reflow, so long
 * content is pre-wrapped into several rows at insert time. Honors embedded
 * newlines and hard-breaks tokens longer than the width so a runaway token
 * (a 400-char JSON blob) cannot overflow the pane.
 */
final class Wrap {

    private Wrap() {
    }

    static List<String> wrap(String text, int width) {
        int w = Math.max(20, width);
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String paragraph : text.replace("\r", "").replace("\t", "    ").split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                while (word.length() > w) {                     // hard break
                    if (!line.isEmpty()) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    out.add(word.substring(0, w));
                    word = word.substring(w);
                }
                if (line.isEmpty()) {
                    line.append(word);
                } else if (line.length() + 1 + word.length() <= w) {
                    line.append(' ').append(word);
                } else {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    /** First line, truncated with an ellipsis, for one-row summaries. */
    static String oneLine(String text, int width) {
        if (text == null) {
            return "";
        }
        String s = text.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= width ? s : s.substring(0, Math.max(0, width - 1)) + "…";
    }
}
