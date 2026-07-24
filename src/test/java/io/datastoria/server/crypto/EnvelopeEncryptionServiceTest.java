package io.datastoria.server.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnvelopeEncryptionServiceTest {

  private static final String SECRET_PLAINTEXT = "sk-live-0123456789abcdefABCDEF";
  private static EnvelopeEncryptionService service;

  @BeforeAll
  static void setUp() {
    byte[] key = new byte[32];
    for (int i = 0; i < key.length; i++) {
      key[i] = (byte) i;
    }
    String base64Key = Base64.getEncoder().encodeToString(key);
    MasterKeyProvider provider = new MasterKeyProvider(base64Key);
    service = new EnvelopeEncryptionService(provider);
  }

  @Test
  void encryptDecryptRoundTripRestoresPlaintext() {
    byte[] plaintext = SECRET_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
    var encrypted = service.encrypt(plaintext);
    byte[] decrypted =
        service.decrypt(encrypted.cipherText(), encrypted.nonce(), encrypted.keyVersion());
    assertThat(decrypted).isEqualTo(plaintext);
    assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(SECRET_PLAINTEXT);
  }

  @Test
  void eachEncryptionProducesUniqueNonce() {
    byte[] plaintext = SECRET_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
    var a = service.encrypt(plaintext);
    var b = service.encrypt(plaintext);
    assertThat(a.nonce()).isNotEqualTo(b.nonce());
    assertThat(a.cipherText()).isNotEqualTo(b.cipherText());
  }

  @Test
  void cipherTextDoesNotContainPlaintext() {
    byte[] plaintext = SECRET_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
    var encrypted = service.encrypt(plaintext);
    String cipherHex = bytesToHex(encrypted.cipherText());
    String nonceHex = bytesToHex(encrypted.nonce());
    assertThat(cipherHex).doesNotContain(SECRET_PLAINTEXT);
    assertThat(nonceHex).doesNotContain(SECRET_PLAINTEXT);
    assertThat(cipherHex).doesNotContain("sk-live");
  }

  @Test
  void tamperedCipherTextFailsDecryption() {
    byte[] plaintext = SECRET_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
    var encrypted = service.encrypt(plaintext);
    byte[] tampered = encrypted.cipherText().clone();
    tampered[0] ^= 0x01;
    assertThatThrownBy(() -> service.decrypt(tampered, encrypted.nonce(), encrypted.keyVersion()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void wrongKeyFailsDecryption() {
    byte[] plaintext = SECRET_PLAINTEXT.getBytes(StandardCharsets.UTF_8);
    var encrypted = service.encrypt(plaintext);

    byte[] wrongKeyBytes = new byte[32];
    for (int i = 0; i < wrongKeyBytes.length; i++) {
      wrongKeyBytes[i] = (byte) (255 - i);
    }
    MasterKeyProvider wrongProvider =
        new MasterKeyProvider(Base64.getEncoder().encodeToString(wrongKeyBytes));
    EnvelopeEncryptionService wrongService = new EnvelopeEncryptionService(wrongProvider);

    assertThatThrownBy(
            () ->
                wrongService.decrypt(
                    encrypted.cipherText(), encrypted.nonce(), encrypted.keyVersion()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void maskedHintDoesNotRevealFullSecret() {
    String hint = MaskedHintBuilder.build(SECRET_PLAINTEXT);
    assertThat(hint).isEqualTo("sk-…DEF");
    assertThat(hint.length()).isLessThan(SECRET_PLAINTEXT.length());
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
