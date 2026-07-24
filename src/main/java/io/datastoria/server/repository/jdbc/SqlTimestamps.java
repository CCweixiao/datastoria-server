package io.datastoria.server.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Cross-dialect timestamp conversion. SQLite stores ISO-8601 TEXT; MySQL stores datetime(6). Both
 * drivers accept and return these values as strings via {@link ResultSet#getString(String)}.
 */
final class SqlTimestamps {

  private SqlTimestamps() {}

  /** Formats an {@link Instant} as an ISO-8601 string for storage. */
  static String toParam(Instant instant) {
    return instant == null ? null : instant.toString();
  }

  /**
   * Parses a timestamp column. Handles ISO-8601 (SQLite) and MySQL datetime(6) {@code yyyy-MM-dd
   * HH:mm:ss.SSSSSS} formats.
   */
  static Instant fromParam(ResultSet rs, String column) throws SQLException {
    String s = rs.getString(column);
    if (s == null) {
      return null;
    }
    try {
      return Instant.parse(s);
    } catch (DateTimeParseException e) {
      // MySQL datetime(6) uses a space separator without timezone suffix.
      return LocalDateTime.parse(s.replace(" ", "T")).toInstant(ZoneOffset.UTC);
    }
  }
}
