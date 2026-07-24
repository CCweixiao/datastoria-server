package io.datastoria.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.datastoria.server.crypto.EnvelopeEncryptionService;
import io.datastoria.server.crypto.MasterKeyProvider;

/** Wires the AES-GCM envelope encryption service as a singleton bean. */
@Configuration
public class CryptoConfig {

  @Bean
  EnvelopeEncryptionService envelopeEncryptionService(MasterKeyProvider keyProvider) {
    return new EnvelopeEncryptionService(keyProvider);
  }
}
