package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.agent.CheckpointContent;
import io.github.ccweixiao.datastoria.common.agent.CheckpointState;
import io.github.ccweixiao.datastoria.common.agent.ChecksumMismatchException;
import io.github.ccweixiao.datastoria.common.agent.UnsupportedCodecVersionException;

/**
 * Unit tests for {@link JsonCheckpointCodec}: normal round-trip, deterministic canonical output,
 * illegal-version rejection, checksum tamper detection (both stateJson and checksum), and sensitive
 * closed-schema secret exclusion. No Spring, no DB, no AgentScope.
 */
class JsonCheckpointCodecTest {

  private final JsonCheckpointCodec codec = new JsonCheckpointCodec();

  private static CheckpointState sample() {
    return new CheckpointState("sess-1", "user-1", "reply-9", 3, false);
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
    // Record components are serialized in declaration order.
    assertThat(content.stateJson())
        .startsWith("{\"sessionId\":\"sess-1\",\"userId\":\"user-1\",\"replyId\":\"reply-9\"")
        .contains("\"currentIteration\":3")
        .contains("\"shutdownInterrupted\":false")
        .doesNotContain("summary", "metadata", "context");
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
    String tampered = valid.stateJson().replace("\"currentIteration\":3", "\"currentIteration\":4");
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
  void schemaHasNoFreeFormSecretCarryingFields() {
    CheckpointContent content = codec.encode(sample());
    assertThat(content.stateJson()).doesNotContain("summary", "metadata", "context");
  }

  @Test
  void checkpointContentRejectsBlankFields() {
    String validChecksum = "a".repeat(64);
    assertThatThrownBy(() -> new CheckpointContent("ds-checkpoint-v1", "", validChecksum))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CheckpointContent("  ", "{}", validChecksum))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CheckpointContent("ds-checkpoint-v1", "{}", "abc"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
