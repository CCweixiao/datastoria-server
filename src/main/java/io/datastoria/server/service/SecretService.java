package io.datastoria.server.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import io.datastoria.server.crypto.EnvelopeEncryptionService;
import io.datastoria.server.crypto.MaskedHintBuilder;
import io.datastoria.server.domain.Secret;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.repository.SecretRepository;

/**
 * Wraps {@link EnvelopeEncryptionService} and {@link SecretRepository}. The plaintext value is
 * encrypted here and never persisted, logged, or returned.
 */
@Service
public class SecretService {

  private final EnvelopeEncryptionService crypto;
  private final SecretRepository secretRepo;

  public SecretService(EnvelopeEncryptionService crypto, SecretRepository secretRepo) {
    this.crypto = crypto;
    this.secretRepo = secretRepo;
  }

  /**
   * Encrypts the plaintext and stores the envelope. Returns the masked secret (never plaintext).
   */
  public Secret save(
      String tenantId, String ownerUserId, String secretKind, String plaintext, Instant expiresAt) {
    var encrypted = crypto.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    Secret secret =
        new Secret(
            Ulid.next(),
            tenantId,
            ownerUserId,
            secretKind,
            encrypted.cipherText(),
            encrypted.keyVersion(),
            encrypted.nonce(),
            MaskedHintBuilder.build(plaintext),
            expiresAt,
            Instant.now(),
            Instant.now(),
            null);
    return secretRepo.save(secret);
  }

  public Optional<Secret> findMaskedById(String id, String tenantId) {
    return secretRepo.findMaskedById(id, tenantId);
  }

  public String decrypt(String id, String tenantId) {
    Secret secret =
        secretRepo
            .findEncryptedById(id, tenantId)
            .orElseThrow(() -> new io.datastoria.server.api.error.NotFoundException("Secret", id));
    byte[] plaintext = crypto.decrypt(secret.cipherText(), secret.nonce(), secret.keyVersion());
    try {
      return new String(plaintext, StandardCharsets.UTF_8);
    } finally {
      java.util.Arrays.fill(plaintext, (byte) 0);
    }
  }

  public void delete(String id, String tenantId) {
    secretRepo.softDelete(id, tenantId);
  }
}
