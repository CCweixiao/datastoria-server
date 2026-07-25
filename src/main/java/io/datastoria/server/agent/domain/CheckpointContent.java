package io.datastoria.server.agent.domain;

import java.util.Objects;

/**
 * The output of DataStoria's checkpoint codec: a versioned, integrity-protected serialization of a
 * {@link CheckpointState}. Stored verbatim (as {@code state_json} / {@code codec_version} / {@code
 * checksum}) in {@code ds_agent_checkpoint}; the repository treats it as opaque.
 *
 * <p>{@code codecVersion} binds the payload to a codec generation; {@code checksum} is a SHA-256
 * over the codec version plus canonical {@code stateJson}, recomputed on decode to detect tampering
 * or corruption.
 *
 * <p>AgentScope-free. The AgentScope {@code State} type never appears here.
 */
public record CheckpointContent(String codecVersion, String stateJson, String checksum) {

  public CheckpointContent {
    Objects.requireNonNull(codecVersion, "codecVersion");
    Objects.requireNonNull(stateJson, "stateJson");
    Objects.requireNonNull(checksum, "checksum");
    if (codecVersion.isBlank()) {
      throw new IllegalArgumentException("codecVersion must not be blank");
    }
    if (stateJson.isBlank()) {
      throw new IllegalArgumentException("stateJson must not be blank");
    }
    if (checksum.isBlank()) {
      throw new IllegalArgumentException("checksum must not be blank");
    }
    if (!checksum.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("checksum must be a lowercase SHA-256 hex value");
    }
  }
}
