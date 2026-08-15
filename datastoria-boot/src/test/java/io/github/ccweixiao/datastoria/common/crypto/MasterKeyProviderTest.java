package io.github.ccweixiao.datastoria.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

class MasterKeyProviderTest {

  private static final String PLAINTEXT = "clickhouse-password";

  private static String base64Key(int seed) {
    byte[] key = new byte[32];
    for (int i = 0; i < key.length; i++) {
      key[i] = (byte) (seed + i);
    }
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void decryptFallsBackToLegacyKeyAfterRotation() {
    String oldKey = base64Key(0);
    String newKey = base64Key(100);
    EnvelopeEncryptionService oldService =
        new EnvelopeEncryptionService(new MasterKeyProvider(oldKey));
    var encrypted = oldService.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8));

    EnvelopeEncryptionService rotated =
        new EnvelopeEncryptionService(new MasterKeyProvider(newKey, List.of(oldKey)));
    byte[] decrypted =
        rotated.decrypt(encrypted.cipherText(), encrypted.nonce(), encrypted.keyVersion());
    assertThat(decrypted).isEqualTo(PLAINTEXT.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void decryptWithoutLegacyKeyStillFailsAfterRotation() {
    String oldKey = base64Key(0);
    String newKey = base64Key(100);
    EnvelopeEncryptionService oldService =
        new EnvelopeEncryptionService(new MasterKeyProvider(oldKey));
    var encrypted = oldService.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8));

    EnvelopeEncryptionService rotated =
        new EnvelopeEncryptionService(new MasterKeyProvider(newKey));
    assertThatThrownBy(
            () ->
                rotated.decrypt(encrypted.cipherText(), encrypted.nonce(), encrypted.keyVersion()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void blankLegacyKeysAreIgnored() {
    MasterKeyProvider provider = new MasterKeyProvider(base64Key(0), Arrays.asList("", null, "  "));
    assertThat(provider.legacyKeys()).isEmpty();
  }

  @Test
  void wrongLengthKeyIsRejected() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> new MasterKeyProvider(shortKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("exactly 32 bytes");
  }
}
