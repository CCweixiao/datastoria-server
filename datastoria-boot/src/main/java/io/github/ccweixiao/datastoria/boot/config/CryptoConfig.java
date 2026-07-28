package io.github.ccweixiao.datastoria.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ccweixiao.datastoria.common.crypto.EnvelopeEncryptionService;
import io.github.ccweixiao.datastoria.common.crypto.MasterKeyProvider;

/** Wires the AES-GCM envelope encryption service as a singleton bean. */
@Configuration
public class CryptoConfig {

  @Bean
  EnvelopeEncryptionService envelopeEncryptionService(MasterKeyProvider keyProvider) {
    return new EnvelopeEncryptionService(keyProvider);
  }
}
