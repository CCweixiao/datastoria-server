package io.datastoria.server.agent.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.datastoria.server.agent.domain.CheckpointCodec;
import io.datastoria.server.agent.domain.CheckpointContent;
import io.datastoria.server.agent.domain.CheckpointState;
import io.datastoria.server.agent.domain.ChecksumMismatchException;
import io.datastoria.server.agent.domain.UnsupportedCodecVersionException;

/**
 * JSON {@link CheckpointCodec}. Canonical form is the sorted-key, compact JSON serialization of
 * {@link CheckpointState}, so the checksum is stable regardless of how a database re-formats the
 * stored {@code json}/{@code TEXT} column (MySQL re-serializes JSON on read; SQLite stores
 * verbatim).
 *
 * <p>Checksum is SHA-256 over {@code "<codecVersion>\n<canonicalStateJson>"} (hex), binding the
 * payload to its codec version. Decode rejects unknown versions and any checksum mismatch.
 *
 * <p>{@link CheckpointState} is a closed schema containing only identifiers, counters and flags.
 * Free-form summary, context and metadata are excluded structurally, rather than relying on
 * best-effort secret-pattern redaction.
 *
 * <p>AgentScope-free — uses only Jackson and the JDK. Lives in the runtime layer alongside the
 * adapter that bridges to AgentScope {@code State}.
 */
public final class JsonCheckpointCodec implements CheckpointCodec {

  private final ObjectMapper mapper;

  public JsonCheckpointCodec() {
    this(baseMapper());
  }

  public JsonCheckpointCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public CheckpointContent encode(CheckpointState state) {
    String stateJson = canonicalize(state);
    String checksum = checksum(CURRENT_VERSION, stateJson);
    return new CheckpointContent(CURRENT_VERSION, stateJson, checksum);
  }

  @Override
  public CheckpointState decode(CheckpointContent content) {
    if (!CURRENT_VERSION.equals(content.codecVersion())) {
      throw new UnsupportedCodecVersionException(content.codecVersion(), CURRENT_VERSION);
    }
    CheckpointState parsed = parse(content.stateJson());
    String canonical = canonicalize(parsed);
    String expected = checksum(content.codecVersion(), canonical);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        content.checksum().getBytes(StandardCharsets.US_ASCII))) {
      throw new ChecksumMismatchException();
    }
    return parsed;
  }

  private String canonicalize(CheckpointState state) {
    try {
      return mapper.writeValueAsString(state);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Failed to canonicalize checkpoint state", e);
    }
  }

  private CheckpointState parse(String stateJson) {
    try {
      return mapper.readValue(stateJson, CheckpointState.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Checkpoint stateJson is not valid JSON", e);
    }
  }

  private static String checksum(String codecVersion, String canonicalStateJson) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash =
          digest.digest(
              (codecVersion + "\n" + canonicalStateJson).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static ObjectMapper baseMapper() {
    ObjectMapper mapper = new ObjectMapper();
    // Records serialize their components in declaration order (deterministic); sort Map keys so the
    // metadata payload is stable regardless of how a DB re-formats the stored JSON column.
    mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    return mapper;
  }
}
