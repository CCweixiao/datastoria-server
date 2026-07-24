package io.datastoria.server.crypto;

/**
 * Builds a human-readable masked hint for display in the UI (e.g. {@code sk-…9x2}). Never returns
 * more than a few characters of the prefix and suffix; the middle is always ellided.
 */
public final class MaskedHintBuilder {

  private static final int PREFIX = 3;
  private static final int SUFFIX = 3;

  private MaskedHintBuilder() {}

  public static String build(String plaintext) {
    if (plaintext == null || plaintext.isEmpty()) {
      return "";
    }
    if (plaintext.length() <= PREFIX + SUFFIX) {
      return "…";
    }
    return plaintext.substring(0, PREFIX) + "…" + plaintext.substring(plaintext.length() - SUFFIX);
  }
}
