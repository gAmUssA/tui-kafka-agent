package com.example.agent.tui;

import com.williamcallahan.tui4j.compat.bubbles.textarea.Textarea;
import com.williamcallahan.tui4j.compat.bubbles.viewport.Viewport;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.message.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.WindowSizeMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Root TUI model implementing the Elm Architecture via tui4j.
 * Composes a Textarea for input and a Viewport for scrollable chat history.
 */
public class AgentModel implements Model {

  private final Textarea textarea;
  private final Viewport viewport;
  private final List<ChatEntry> chatHistory = new ArrayList<>();
  private int width = 80;
  private int height = 24;

  private static final int INPUT_HEIGHT = 3;
  private static final int HEADER_HEIGHT = 2;

  public AgentModel() {
    textarea = new Textarea();
    textarea.setPlaceholder("Type a message... (/ for commands)");
    textarea.setWidth(78);
    textarea.setHeight(INPUT_HEIGHT);
    textarea.setShowLineNumbers(false);
    textarea.setCharLimit(0);
    textarea.focus();

    viewport = Viewport.create(78, 18);
    viewport.setContent("  Welcome to kafka-agent. Type a message to begin.\n");
  }

  @Override
  public Command init() {
    return textarea.init();
  }

  @Override
  public UpdateResult<? extends Model> update(Message msg) {
    if (msg instanceof KeyPressMessage keyMsg) {
      // Quit on Ctrl+C
      if ("ctrl+c".equals(keyMsg.key())) {
        return UpdateResult.from(this, QuitMessage::new);
      }

      // Enter to submit message (intercept before textarea gets it)
      if ("enter".equals(keyMsg.key())) {
        String text = textarea.value().trim();
        if (!text.isEmpty()) {
          chatHistory.add(ChatEntry.user(text));
          textarea.reset();
          refreshViewport();
        }
        return UpdateResult.from(this);
      }
    }

    // Handle window resize
    if (msg instanceof WindowSizeMessage(int width1, int height1)) {
      this.width = width1;
      this.height = height1;
      textarea.setWidth(width - 2);
      viewport.setWidth(width - 2);
      viewport.setHeight(height - INPUT_HEIGHT - HEADER_HEIGHT - 2);
      return UpdateResult.from(this);
    }

    // Let viewport handle scroll keys (pgup, pgdn, etc.)
    viewport.update(msg);

    // Delegate all other input to textarea
    UpdateResult<? extends Model> result = textarea.update(msg);
    return UpdateResult.from(this, result.command());
  }

  @Override
  public String view() {
    String header = "kafka-agent | Ctrl+C to quit";
    String separator = "─".repeat(Math.max(1, width));

    return header + "\n"
           + separator + "\n"
           + viewport.view() + "\n"
           + separator + "\n"
           + textarea.view();
  }

  private void refreshViewport() {
    var sb = new StringBuilder();
    for (ChatEntry entry : chatHistory) {
      String prefix = switch (entry.role()) {
        case USER -> "  You: ";
        case AGENT -> "  Agent: ";
        case TOOL -> "  Tool: ";
        case ERROR -> "  Error: ";
      };
      sb.append(prefix).append(entry.content()).append("\n\n");
    }
    viewport.setContent(sb.toString());
    viewport.gotoBottom();
  }
}
