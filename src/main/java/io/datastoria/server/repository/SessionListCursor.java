package io.datastoria.server.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Encodes and parses the opaque session-list cursor. Wire format (frozen for parity with Node):
 *
 * <pre>{@code
 * YYYY-MM-DD HH:MM:SS.mmm|<session_id>
 * }</pre>
 *
 * <p>The timestamp is UTC, zero-padded, three-digit milliseconds. The cursor encodes the {@code
 * (updated_at, session_id)} of the last row returned so that keyset pagination on {@code
 * (updated_at DESC, session_id DESC)} is stable.
 *
 * <p>Clients MUST URL-encode the value before placing it in a query string (the raw value contains
 * a space and a pipe). A malformed cursor parses to {@link Optional#empty} — the caller then
 * silently returns page 1 and emits a warn log, matching Node behaviour.
 */
public final class SessionListCursor {

  // 2026-07-25 09:00:00.000 (space separator, 3-digit millis, UTC).
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  private final Instant updatedAt;
  private final String sessionId;

  private SessionListCursor(Instant updatedAt, String sessionId) {
    this.updatedAt = updatedAt;
    this.sessionId = sessionId;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public String sessionId() {
    return sessionId;
  }

  /** Encodes a cursor for the given last-row coordinates. */
  public static String encode(Instant updatedAt, String sessionId) {
    return FORMAT.format(updatedAt) + "|" + sessionId;
  }

  /**
   * Parses a cursor string. Returns empty for null/blank input or any value that does not match the
   * documented format.
   */
  public static Optional<SessionListCursor> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    int sep = raw.lastIndexOf('|');
    if (sep <= 0 || sep >= raw.length() - 1) {
      return Optional.empty();
    }
    String ts = raw.substring(0, sep);
    String id = raw.substring(sep + 1);
    try {
      Instant parsed = FORMAT.parse(ts, Instant::from);
      return Optional.of(new SessionListCursor(parsed, id));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
