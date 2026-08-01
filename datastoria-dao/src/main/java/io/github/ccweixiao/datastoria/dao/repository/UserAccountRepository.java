package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.UserAccount;

/** Repository for local user accounts ({@code ds_user_account}). */
public interface UserAccountRepository {

  UserAccount save(UserAccount account);

  Optional<UserAccount> findByUsername(String username);

  Optional<UserAccount> findByUserId(String userId);

  Optional<UserAccount> findByTenantIdAndUserId(String tenantId, String userId);

  List<UserAccount> findAll(String tenantId);

  /** Updates mutable fields (role, status, email, passwordHash); bumps {@code updated_at}. */
  UserAccount update(UserAccount account);

  boolean existsByUsername(String username);
}
