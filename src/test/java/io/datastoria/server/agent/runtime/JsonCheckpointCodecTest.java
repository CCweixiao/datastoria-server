package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.datastoria.server.agent.domain.CheckpointContent;
import io.datastoria.server.agent.domain.CheckpointState;
import io.datastoria.server.agent.domain.ChecksumMismatchException;
import io.datastoria.server.agent.domain.UnsupportedCodecVersionException;

/**
 * Unit tests for {@link JsonCheckpointCodec}: normal round-trip, deterministic canonical output,
 * illegal-version rejection, checksum tamper detection (both stateJson and checksum), and sensitive
 * field redaction. No Spring, no DB, no AgentScope.
 */
class JsonCheckpointCodecTest {

  private final JsonCheckpointCodec codec = new JsonCheckpointCodec();

  private static CheckpointState sample() {
    return new CheckpointState(
        "sess-1", "user-1", "reply-9", 3, "harmless summary", false, Map.of("phase", "reasoning"));
  }

  @Test
  void roundTripPreservesState() {
    CheckpointContent content = codec.encode(sample());

    CheckpointState decoded = codec.decode(content);

    assertThat(decoded).isEqualTo(sample());
    assertThat(content.codecVersion()).isEqualTo("ds-checkpoint-v1");
    assertThat(content.checksum()).hasSize(64); // SHA-256 hex
  }

  @Test
  void encodingIsDeterministic() {
    CheckpointContent a = codec.encode(sample());
    CheckpointContent b = codec.encode(sample());
    assertThat(b.stateJson()).isEqualTo(a.stateJson());
    assertThat(b.checksum()).isEqualTo(a.checksum());
  }

  @Test
  void stateJsonIsCanonicalSortedObject() {
    CheckpointContent content = codec.encode(sample());
    // Record components in declaration order; metadata map keys sorted.
    assertThat(content.stateJson())
        .startsWith("{\"sessionId\":\"sess-1\",\"userId\":\"user-1\",\"replyId\":\"reply-9\"")
        .contains("\"currentIteration\":3")
        .contains("\"summary\":\"harmless summary\"")
        .contains("\"metadata\":{\"phase\":\"reasoning\"}");
  }

  @Test
  void decodeRejectsUnknownCodecVersion() {
    CheckpointContent valid = codec.encode(sample());
    CheckpointContent wrongVersion =
        new CheckpointContent("ds-checkpoint-v0", valid.stateJson(), valid.checksum());

    assertThatThrownBy(() -> codec.decode(wrongVersion))
        .isInstanceOf(UnsupportedCodecVersionException.class);
  }

  @Test
  void decodeDetectsTamperedStateJson() {
    CheckpointContent valid = codec.encode(sample());
    String tampered = valid.stateJson().replace("harmless summary", "EVIL summary");
    CheckpointContent tamperedContent =
        new CheckpointContent(valid.codecVersion(), tampered, valid.checksum());

    assertThatThrownBy(() -> codec.decode(tamperedContent))
        .isInstanceOf(ChecksumMismatchException.class);
  }

  @Test
  void decodeDetectsTamperedChecksum() {
    CheckpointContent valid = codec.encode(sample());
    String bogusChecksum = valid.checksum().substring(0, 62) + "ff";
    if (bogusChecksum.equals(valid.checksum())) {
      // ensure it actually differs
      return;
    }
    CheckpointContent tampered =
        new CheckpointContent(valid.codecVersion(), valid.stateJson(), bogusChecksum);

    assertThatThrownBy(() -> codec.decode(tampered)).isInstanceOf(ChecksumMismatchException.class);
  }

  @Test
  void decodeRejectsBlankChecksumFromLegacyRow() {
    CheckpointContent valid = codec.encode(sample());
    assertThatThrownBy(() -> new CheckpointContent(valid.codecVersion(), valid.stateJson(), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sensitiveMetadataKeysAreRedacted() {
    CheckpointState state =
        new CheckpointState(
            "s", "u", "r", 0, null, false, Map.of("api_key", "sk-SECRET-123", "safeFlag", "ok"));

    CheckpointContent content = codec.encode(state);

    assertThat(content.stateJson()).contains("[REDACTED]");
    assertThat(content.stateJson()).doesNotContain("sk-SECRET-123");
    assertThat(content.stateJson()).contains("\"safeFlag\":\"ok\"");
    // Redacted value survives round-trip.
    CheckpointState decoded = codec.decode(content);
    assertThat(decoded.metadata().get("api_key")).isEqualTo("[REDACTED]");
    assertThat(decoded.metadata().get("safeFlag")).isEqualTo("ok");
  }

  @Test
  void checkpointContentRejectsBlankFields() {
    assertThatThrownBy(() -> new CheckpointContent("ds-checkpoint-v1", "", "abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CheckpointContent("  ", "{}", "abc"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
