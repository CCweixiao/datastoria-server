package io.github.ccweixiao.datastoria.boot.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ccweixiao.datastoria.common.crypto.MasterKeyProvider;

/**
 * Resolves the credential-encryption master key.
 *
 * <ul>
 *   <li>{@code datastoria.master-key} (env {@code DATASTORIA_MASTER_KEY}, or the built-in dev
 *       profile default) wins when set.
 *   <li>Otherwise the key is read from — or on first start generated into — the key file at {@code
 *       datastoria.master-key-file} (default {@code data/master.key} under the process working
 *       directory; the unified package runs from the install root, so this lands in the package's
 *       {@code data/} directory).
 * </ul>
 *
 * <p>{@code datastoria.master-key-legacy} lists decrypt-only keys retained across rotations; see
 * {@link MasterKeyProvider#legacyKeys()}.
 */
@Configuration
public class MasterKeyConfig {

  @Bean
  MasterKeyProvider masterKeyProvider(
      @Value("${datastoria.master-key:}") String configuredKey,
      @Value("${datastoria.master-key-file:data/master.key}") String keyFilePath,
      @Value("${datastoria.master-key-legacy:}") List<String> legacyKeys) {
    String activeKey =
        configuredKey != null && !configuredKey.isBlank()
            ? configuredKey
            : MasterKeyFile.loadOrGenerate(Path.of(keyFilePath));
    return new MasterKeyProvider(activeKey, legacyKeys);
  }
}
