package io.github.ccweixiao.datastoria.common.crypto;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides the AES-256 master key(s) used by {@link EnvelopeEncryptionService}. The active key is a
 * base64-encoded 32-byte value resolved by the boot configuration (env var, profile default, or
 * auto-generated key file).
 *
 * <p>Key versioning: the active key is always version {@code "v1"}. {@link #keyForVersion(String)}
 * falls back to the active key when a version is not found. Additional legacy keys can be
 * registered for decryption only; {@link EnvelopeEncryptionService#decrypt} tries them when the
 * active key fails, so rotating the active key does not brick existing ciphertexts.
 */
public class MasterKeyProvider {

  private static final String ACTIVE_VERSION = "v1";
  private static final int KEY_LENGTH_BYTES = 32; // AES-256

  private final SecretKey activeKey;
  private final List<SecretKey> legacyKeys;
  private final Map<String, SecretKey> historicalKeys;

  public MasterKeyProvider(String base64Key) {
    this(base64Key, List.of());
  }

  public MasterKeyProvider(String base64Key, List<String> legacyBase64Keys) {
    this.activeKey = decodeActive(base64Key);
    this.legacyKeys =
        legacyBase64Keys.stream()
            .filter(key -> key != null && !key.isBlank())
            .map(MasterKeyProvider::decodeActive)
            .toList();
    this.historicalKeys = Map.of();
  }

  private static SecretKey decodeActive(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      throw new IllegalStateException(
          "datastoria.master-key is not set. Configure a base64-encoded 32-byte key.");
    }
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "datastoria.master-key must decode to exactly "
              + KEY_LENGTH_BYTES
              + " bytes for AES-256, got "
              + keyBytes.length);
    }
    return new SecretKeySpec(keyBytes, "AES");
  }

  /** Returns the currently active key version identifier. */
  public String activeVersion() {
    return ACTIVE_VERSION;
  }

  /** Returns the active {@link SecretKey} for encryption. */
  public SecretKey activeKey() {
    return activeKey;
  }

  /** Returns decrypt-only legacy keys, oldest first; empty when no rotation has occurred. */
  public List<SecretKey> legacyKeys() {
    return legacyKeys;
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
