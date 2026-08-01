package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * A local user account. {@code userId} is an application-assigned ULID reused as the opaque {@code
 * owner_user_id} across the rest of the schema; {@code tenantId} is a single default value until
 * multi-organization support is introduced. {@code passwordHash} is a BCrypt digest and is nullable
 * so future SSO-only accounts can share this table.
 */
public record UserAccount(
    String userId,
    String tenantId,
    String username,
    String email,
    String passwordHash,
    String role,
    int status,
    Instant createdAt,
    Instant updatedAt) {

  public static final String ROLE_USER = "USER";
  public static final String ROLE_ADMIN = "ADMIN";

  /** Enabled accounts have {@code status == 1}. */
  public boolean enabled() {
    return status == 1;
  }

  public boolean isAdmin() {
    return ROLE_ADMIN.equals(role);
  }
}
