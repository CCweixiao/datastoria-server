package io.github.ccweixiao.datastoria.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.ccweixiao.datastoria.common.agent.CheckpointContent;
import io.github.ccweixiao.datastoria.common.agent.PendingActionCheckpoint;

/** Canonical, checksummed codec for P8 permission-pause recovery payloads. */
@Component
public final class PendingActionCheckpointCodec {

  public static final String VERSION = "pending-action-v1";

  private final ObjectMapper mapper;

  public PendingActionCheckpointCodec(ObjectMapper mapper) {
    this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  public CheckpointContent encode(PendingActionCheckpoint state) {
    try {
      String json = mapper.writeValueAsString(state);
      return new CheckpointContent(VERSION, json, checksum(json));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encode pending action checkpoint", e);
    }
  }

  public PendingActionCheckpoint decode(CheckpointContent content) {
    if (!VERSION.equals(content.codecVersion())) {
      throw new IllegalArgumentException(
          "Unsupported pending action checkpoint version: " + content.codecVersion());
    }
    try {
      PendingActionCheckpoint state =
          mapper.readValue(content.stateJson(), PendingActionCheckpoint.class);
      String canonical = mapper.writeValueAsString(state);
      if (!MessageDigest.isEqual(
          checksum(canonical).getBytes(StandardCharsets.US_ASCII),
          content.checksum().getBytes(StandardCharsets.US_ASCII))) {
        throw new IllegalArgumentException("Pending action checkpoint checksum mismatch");
      }
      return state;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decode pending action checkpoint", e);
    }
  }

  private static String checksum(String json) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((VERSION + "\n" + json).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
