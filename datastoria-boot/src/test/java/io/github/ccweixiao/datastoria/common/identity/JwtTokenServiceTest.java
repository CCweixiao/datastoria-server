package io.github.ccweixiao.datastoria.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.config.SecurityProperties;
import io.github.ccweixiao.datastoria.common.crypto.MasterKeyProvider;

class JwtTokenServiceTest {

  private static final String MASTER_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

  private JwtTokenService service() {
    SecurityProperties properties = new SecurityProperties();
    properties.getJwt().setSecret("test-secret");
    properties.getJwt().setIssuer("datastoria-test");
    properties.getJwt().setAudience("datastoria-test-api");
    properties.getJwt().setTtlMinutes(60);
    MasterKeyProvider masterKey = new MasterKeyProvider(MASTER_KEY);
    return new JwtTokenService(properties, masterKey);
  }

  @Test
  void signAndVerifyRoundTripsClaims() {
    JwtTokenService service = service();
    String token = service.sign("user-123", "default", "ADMIN", "alice", 3);

    var verified = service.parseAndVerify(token);
    assertThat(verified).isPresent();
    assertThat(verified.get().userId()).isEqualTo("user-123");
    assertThat(verified.get().tenantId()).isEqualTo("default");
    assertThat(verified.get().role()).isEqualTo("ADMIN");
    assertThat(verified.get().username()).isEqualTo("alice");
    assertThat(verified.get().tokenVersion()).isEqualTo(3);
  }

  @Test
  void garbageTokenIsRejected() {
    assertThat(service().parseAndVerify("not-a-jwt")).isEmpty();
  }

  @Test
  void tokenSignedWithDifferentSecretIsRejected() {
    SecurityProperties other = new SecurityProperties();
    other.getJwt().setSecret("different-secret");
    other.getJwt().setIssuer("datastoria-test");
    other.getJwt().setAudience("datastoria-test-api");
    MasterKeyProvider masterKey = new MasterKeyProvider(MASTER_KEY);
    String token = new JwtTokenService(other, masterKey).sign("u", "default", "USER", "bob", 1);

    // Same key material but different effective secret hash -> signature mismatch.
    SecurityProperties mine = new SecurityProperties();
    mine.getJwt().setSecret("test-secret");
    mine.getJwt().setIssuer("datastoria-test");
    mine.getJwt().setAudience("datastoria-test-api");
    JwtTokenService verifier = new JwtTokenService(mine, masterKey);

    assertThat(verifier.parseAndVerify(token)).isEmpty();
  }
}
