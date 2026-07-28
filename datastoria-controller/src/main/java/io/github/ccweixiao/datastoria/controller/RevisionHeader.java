package io.github.ccweixiao.datastoria.controller;

/** Parses the revision carried by an HTTP {@code If-Match} entity-tag header. */
public final class RevisionHeader {

  private RevisionHeader() {}

  public static Long parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.startsWith("W/")) {
      normalized = normalized.substring(2).trim();
    }
    if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    try {
      return Long.valueOf(normalized);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("If-Match must contain a numeric revision");
    }
  }
}
