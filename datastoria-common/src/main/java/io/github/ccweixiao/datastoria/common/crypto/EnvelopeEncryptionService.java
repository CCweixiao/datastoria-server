package io.github.ccweixiao.datastoria.common.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM envelope encryption for secrets. Each {@link #encrypt(byte[])} call generates a fresh
 * 12-byte nonce and produces cipher text that includes the 128-bit authentication tag.
 *
 * <p>The stored envelope is {@code (cipherText, nonce, keyVersion)}. Decryption requires the same
 * three values. A tampered cipher text or nonce fails with {@link
 * javax.crypto.AEADBadTagException}.
 */
public class EnvelopeEncryptionService {

  private static final int NONCE_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";

  private final MasterKeyProvider keyProvider;
  private final SecureRandom random = new SecureRandom();

  public EnvelopeEncryptionService(MasterKeyProvider keyProvider) {
    this.keyProvider = keyProvider;
  }

  /** Encrypts plaintext, returning the cipher text, nonce and key version. */
  public EncryptedSecret encrypt(byte[] plaintext) {
    try {
      byte[] nonce = new byte[NONCE_LENGTH_BYTES];
      random.nextBytes(nonce);
      SecretKey key = keyProvider.activeKey();
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      byte[] cipherText = cipher.doFinal(plaintext);
      return new EncryptedSecret(cipherText, nonce, keyProvider.activeVersion());
    } catch (Exception e) {
      throw new IllegalStateException("AES-GCM encryption failed", e);
    }
  }

  /**
   * Decrypts using the matching nonce. The active key is tried first; if its GCM tag does not
   * verify, each registered legacy key is tried (oldest first) so ciphertexts written before a key
   * rotation stay readable. A tampered envelope or a key that was never registered fails with
   * {@link IllegalStateException}.
   */
  public byte[] decrypt(byte[] cipherText, byte[] nonce, String keyVersion) {
    SecretKey key = keyProvider.keyForVersion(keyVersion);
    Exception lastFailure = null;
    for (SecretKey candidate : candidates(key)) {
      try {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, candidate, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        return cipher.doFinal(cipherText);
      } catch (Exception e) {
        lastFailure = e;
      }
    }
    throw new IllegalStateException("AES-GCM decryption failed", lastFailure);
  }

  /** Active key first, then the decrypt-only legacy keys, deduplicated. */
  private List<SecretKey> candidates(SecretKey key) {
    List<SecretKey> candidates = new ArrayList<>(1 + keyProvider.legacyKeys().size());
    candidates.add(key);
    for (SecretKey legacy : keyProvider.legacyKeys()) {
      if (!Arrays.equals(legacy.getEncoded(), key.getEncoded())) {
        candidates.add(legacy);
      }
    }
    return candidates;
  }

  /**
   * Result of {@link #encrypt(byte[])} — the three fields persisted to {@code ds_secret}. Never
   * logged or returned in API responses.
   */
  public record EncryptedSecret(byte[] cipherText, byte[] nonce, String keyVersion) {

    /** Concatenates nonce + cipherText for compact single-column blob storage. */
    public byte[] envelope() {
      return ByteBuffer.allocate(nonce.length + cipherText.length)
          .put(nonce)
          .put(cipherText)
          .array();
    }
  }
}
