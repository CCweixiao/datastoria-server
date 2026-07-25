package io.datastoria.server.agent.domain;

/**
 * Raised by {@link CheckpointCodec#decode} when a checkpoint's {@code codecVersion} is not the
 * current {@link CheckpointCodec#CURRENT_VERSION}. A future codec that can migrate old versions
 * would catch this; P4.4 treats unknown versions as non-decodable so stale/incompatible blobs are
 * never silently misparsed.
 */
public class UnsupportedCodecVersionException extends RuntimeException {

  private final String encountered;
  private final String expected;

  public UnsupportedCodecVersionException(String encountered, String expected) {
    super("Unsupported checkpoint codec version: " + encountered + " (expected " + expected + ")");
    this.encountered = encountered;
    this.expected = expected;
  }

  public String encountered() {
    return encountered;
  }

  public String expected() {
    return expected;
  }
}
