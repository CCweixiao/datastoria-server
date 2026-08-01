package io.github.ccweixiao.datastoria.common.dto;

import java.time.Instant;

import io.github.ccweixiao.datastoria.common.domain.UserAccount;

/** Safe projection of a user account; never exposes {@code passwordHash}. */
public record UserResponse(
    String userId,
    String username,
    String email,
    String role,
    String tenantId,
    int status,
    Instant createdAt) {

  public static UserResponse from(UserAccount a) {
    return new UserResponse(
        a.userId(), a.username(), a.email(), a.role(), a.tenantId(), a.status(), a.createdAt());
  }
}
