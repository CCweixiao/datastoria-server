package io.datastoria.server.identity;

import java.util.Set;

/**
 * Resolved identity for the current request. Never trusts a client-supplied {@code tenantId} — that
 * always comes from server-side configuration or an authenticated session.
 */
public record Identity(String tenantId, String userId, Set<String> roles) {

  public boolean hasRole(String role) {
    return roles != null && roles.contains(role);
  }

  public boolean isAdmin() {
    return hasRole("ROLE_ADMIN");
  }
}
