package io.github.ccweixiao.datastoria.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Resolved configuration for local username+password authentication and JWT issuance/verification
 * ({@code datastoria.security.*}). The HS256 signing key is resolved by {@code JwtTokenService}
 * from {@link Jwt#getSecret()}, falling back to the application master key when unset. Login JWTs
 * use a dedicated audience/issuer so they can never be confused with session-share JWTs.
 */
@Component
@ConfigurationProperties(prefix = "datastoria.security")
@Validated
public class SecurityProperties {

  /** Single default tenant assigned to every account until multi-organization support lands. */
  @NotBlank private String defaultTenant = "default";

  @Valid private final Jwt jwt = new Jwt();
  @Valid private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

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
    @NotBlank private String issuer = "datastoria";
    @NotBlank private String audience = "datastoria-api";

    @Min(1)
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
    @NotBlank private String username = "datastoria";
    private String password = "";

    @Pattern(regexp = "USER|ADMIN")
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
