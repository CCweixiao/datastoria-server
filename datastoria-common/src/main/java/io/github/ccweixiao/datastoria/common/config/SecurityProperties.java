package io.github.ccweixiao.datastoria.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Resolved configuration for local username+password authentication and JWT issuance/verification
 * ({@code datastoria.security.*}). The HS256 signing key is resolved by {@code JwtTokenService}
 * from {@link Jwt#getSecret()}, falling back to the application master key when unset. Login JWTs
 * use a dedicated audience/issuer so they can never be confused with session-share JWTs.
 */
@Component
@ConfigurationProperties(prefix = "datastoria.security")
public class SecurityProperties {

  /** Single default tenant assigned to every account until multi-organization support lands. */
  private String defaultTenant = "default";

  private final Jwt jwt = new Jwt();
  private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

  public String getDefaultTenant() {
    return defaultTenant;
  }

  public void setDefaultTenant(String defaultTenant) {
    this.defaultTenant = defaultTenant;
  }

  public Jwt getJwt() {
    return jwt;
  }

  public BootstrapAdmin getBootstrapAdmin() {
    return bootstrapAdmin;
  }

  /** HS256 signing parameters for login JWTs. */
  public static class Jwt {
    private String secret = "";
    private String issuer = "datastoria";
    private String audience = "datastoria-api";
    private long ttlMinutes = 480L;

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public String getIssuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }

    public String getAudience() {
      return audience;
    }

    public void setAudience(String audience) {
      this.audience = audience;
    }

    public long getTtlMinutes() {
      return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
      this.ttlMinutes = ttlMinutes;
    }
  }

  /** Credentials for the idempotent administrator created on first startup. */
  public static class BootstrapAdmin {
    private String username = "admin";
    private String password = "";
    private String role = "ADMIN";
    private String tenant;
    private String email;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public String getTenant() {
      return tenant;
    }

    public void setTenant(String tenant) {
      this.tenant = tenant;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }
}
