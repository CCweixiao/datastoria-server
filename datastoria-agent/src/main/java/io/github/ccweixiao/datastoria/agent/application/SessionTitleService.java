package io.github.ccweixiao.datastoria.agent.application;

import org.springframework.stereotype.Service;

/**
 * Deterministic fallback title boundary for A01. The selected server-side model generates the final
 * title; this fallback keeps title failure or timeout independent from the primary answer.
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
