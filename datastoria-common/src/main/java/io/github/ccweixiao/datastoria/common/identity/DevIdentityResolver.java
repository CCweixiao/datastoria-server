package io.github.ccweixiao.datastoria.common.identity;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Resolves a dev {@link Identity} from the {@code x-datastoria-user-email} header using
 * configuration defaults. In the dev/test environment, identity characteristics are
 * convention-based:
 *
 * <ul>
 *   <li>Emails listed in {@code datastoria.identity.admin-users} get {@code ROLE_ADMIN}.
 *   <li>Other emails get {@code ROLE_USER} only.
 *   <li>Tenant is derived from the email prefix before {@code @} when the prefix matches a known
 *       pattern ({@code tenant-*}), otherwise the default tenant.
 * </ul>
 *
 * P10 will replace this entirely with OAuth.
 */
@Component
@Profile("dev")
public class DevIdentityResolver {

  private final String defaultTenant;
  private final String defaultUser;
  private final String defaultRoles;
  private final Set<String> adminUsers;

  public DevIdentityResolver(
      @Value("${datastoria.identity.default-tenant:tenant-default}") String defaultTenant,
      @Value("${datastoria.identity.default-user:}") String defaultUser,
      @Value("${datastoria.identity.default-roles:ROLE_USER}") String defaultRoles,
      @Value("${datastoria.identity.admin-users:dev@example.com}") String adminUsers) {
    this.defaultTenant = defaultTenant;
    this.defaultUser = defaultUser;
    this.defaultRoles = defaultRoles;
    this.adminUsers =
        Arrays.stream(adminUsers.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
  }

  public Identity resolve(String emailHeader) {
    String userId = (emailHeader == null || emailHeader.isBlank()) ? defaultUser : emailHeader;
    String tenant = resolveTenant(userId);
    Set<String> roles = resolveRoles(userId);
    return new Identity(tenant, userId, roles);
  }

  private String resolveTenant(String userId) {
    if (userId != null && userId.contains("@")) {
      String prefix = userId.substring(0, userId.indexOf('@'));
      if (prefix.startsWith("tenant-")) {
        return prefix;
      }
    }
    return defaultTenant;
  }

  private Set<String> resolveRoles(String userId) {
    String source = adminUsers.contains(userId) ? defaultRoles : "ROLE_USER";
    return Arrays.stream(source.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }
}
