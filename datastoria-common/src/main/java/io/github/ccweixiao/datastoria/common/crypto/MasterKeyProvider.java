package io.github.ccweixiao.datastoria.common.crypto;

import java.util.Base64;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides the AES-256 master key(s) used by {@link EnvelopeEncryptionService}. The active key is
 * read from the {@code datastoria.master-key} property (mapped from {@code DATASTORIA_MASTER_KEY}
 * env var in production) as a base64-encoded 32-byte value.
 *
 * <p>Key versioning: the active key is always version {@code "v1"}. Additional historical keys can
 * be registered for decryption-only rotation; {@link #keyForVersion(String)} falls back to the
 * active key when a version is not found.
 */
@Component
public class MasterKeyProvider {

  private static final String ACTIVE_VERSION = "v1";
  private static final int KEY_LENGTH_BYTES = 32; // AES-256

  private final SecretKey activeKey;
  private final Map<String, SecretKey> historicalKeys;

  public MasterKeyProvider(@Value("${datastoria.master-key:}") String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException(
          "datastoria.master-key is not set. Provide a base64-encoded 32-byte key"
              + " via the DATASTORIA_MASTER_KEY environment variable.");
    }
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "datastoria.master-key must decode to exactly "
              + KEY_LENGTH_BYTES
              + " bytes for AES-256, got "
              + keyBytes.length);
    }
    this.activeKey = new SecretKeySpec(keyBytes, "AES");
    this.historicalKeys = Map.of();
  }

  /** Returns the currently active key version identifier. */
  public String activeVersion() {
    return ACTIVE_VERSION;
  }

  /** Returns the active {@link SecretKey} for encryption. */
  public SecretKey activeKey() {
    return activeKey;
  }

  /**
   * Resolves a {@link SecretKey} for the given version. Falls back to the active key for unknown
   * versions (rotation is forward-only in this baseline).
   */
  public SecretKey keyForVersion(String version) {
    if (version == null) {
      return activeKey;
    }
    SecretKey historical = historicalKeys.get(version);
    return historical != null ? historical : activeKey;
  }
}
