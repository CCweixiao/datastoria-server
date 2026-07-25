package io.datastoria.server.agent.application;

import org.springframework.stereotype.Service;

/**
 * Independent title boundary for A01. P4 uses a deterministic provisional title; a later provider
 * implementation can replace this bean without coupling title failure or latency to the agent run.
 */
@Service
public class SessionTitleService {

  /** Returns the first eight words, or {@code null} when title generation is disabled/empty. */
  public String generateProvisional(String userText, boolean enabled) {
    if (!enabled || userText == null || userText.isBlank()) {
      return null;
    }
    String[] words = userText.trim().split("\\s+");
    StringBuilder title = new StringBuilder();
    for (int i = 0; i < Math.min(words.length, 8); i++) {
      if (title.length() > 0) {
        title.append(' ');
      }
      title.append(words[i]);
    }
    return title.toString();
  }
}
