package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SessionListCursorTest {

  private static final Instant T = Instant.parse("2026-07-25T09:05:00.123Z");

  @Test
  void encodeProducesNodeCompatibleWireFormat() {
    String cursor = SessionListCursor.encode(T, "sess_01abc");
    assertThat(cursor).isEqualTo("2026-07-25 09:05:00.123|sess_01abc");
  }

  @Test
  void parseRoundTrips() {
    String encoded = SessionListCursor.encode(T, "sess_01abc");
    var parsed = SessionListCursor.parse(encoded).orElseThrow();
    assertThat(parsed.updatedAt()).isEqualTo(T);
    assertThat(parsed.sessionId()).isEqualTo("sess_01abc");
  }

  @Test
  void parseAcceptsClientSuppliedCursorWithSameFormat() {
    var parsed =
        SessionListCursor.parse("2026-07-24 11:02:00.000|019523a0f0a64d6c8a3e2b9c1f0d7e02")
            .orElseThrow();
    assertThat(parsed.updatedAt()).isEqualTo(Instant.parse("2026-07-24T11:02:00Z"));
    assertThat(parsed.sessionId()).isEqualTo("019523a0f0a64d6c8a3e2b9c1f0d7e02");
  }

  @Test
  void parseReturnsEmptyForMalformedCursor() {
    assertThat(SessionListCursor.parse(null)).isEmpty();
    assertThat(SessionListCursor.parse("")).isEmpty();
    assertThat(SessionListCursor.parse("   ")).isEmpty();
    assertThat(SessionListCursor.parse("no-pipe-here")).isEmpty();
    assertThat(SessionListCursor.parse("|no-timestamp")).isEmpty();
    assertThat(SessionListCursor.parse("2026-07-25|")).isEmpty();
    assertThat(SessionListCursor.parse("not-a-timestamp|sess")).isEmpty();
  }
}
