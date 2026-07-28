package io.github.ccweixiao.datastoria.common.agent;

/**
 * Raised by {@link CheckpointCodec#decode} when the stored checksum does not equal the checksum
 * recomputed from the canonical {@code stateJson} — i.e. the payload was tampered with or corrupted
 * in storage/transit. The mismatched content is not returned.
 */
public class ChecksumMismatchException extends RuntimeException {

  public ChecksumMismatchException() {
    super("Checkpoint checksum mismatch: stateJson was tampered with or corrupted");
  }
}
