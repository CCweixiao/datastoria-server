package io.github.ccweixiao.datastoria.common.agent;

/**
 * DataStoria's own checkpoint serializer: encodes a {@link CheckpointState} into an integrity-bound
 * {@link CheckpointContent} and decodes it back, rejecting unknown codec versions and mismatched
 * checksums. Implementations MUST exclude prompt, API key, and provider credential text from the
 * serialized form.
 *
 * <p>The codec is independent of the Agent runtime: it never references AgentScope types, so it is
 * unaffected by an AgentScope upgrade or replacement.
 */
public interface CheckpointCodec {

  /** Current codec generation, persisted in {@code ds_agent_checkpoint.codec_version}. */
  String CURRENT_VERSION = "ds-checkpoint-v1";

  /**
   * Serializes {@code state} to canonical JSON, redacts sensitive keys, computes the checksum, and
   * wraps it with the current codec version.
   */
  CheckpointContent encode(CheckpointState state);

  /**
   * Validates the codec version, recomputes and verifies the checksum, then parses the state.
   *
   * @throws UnsupportedCodecVersionException if {@code content.codecVersion()} is not {@link
   *     #CURRENT_VERSION}.
   * @throws ChecksumMismatchException if the stored checksum does not match the recomputed one
   *     (tampered or corrupted payload).
   */
  CheckpointState decode(CheckpointContent content);
}
