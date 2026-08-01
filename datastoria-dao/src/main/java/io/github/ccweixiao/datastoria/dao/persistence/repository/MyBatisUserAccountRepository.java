package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.UserAccountEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.UserAccountMapper;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

/** MyBatis-Plus adapter for {@code ds_user_account}. */
@Repository
public class MyBatisUserAccountRepository implements UserAccountRepository {

  private final UserAccountMapper mapper;

  public MyBatisUserAccountRepository(UserAccountMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public UserAccount save(UserAccount account) {
    Instant now = Instant.now();
    UserAccountEntity e = UserAccountEntity.fromDomain(account);
    e.setCreatedAt(account.createdAt() != null ? account.createdAt() : now);
    e.setUpdatedAt(account.updatedAt() != null ? account.updatedAt() : now);
    mapper.insert(e);
    return findByUserId(e.getUserId())
        .orElseThrow(() -> new NotFoundException("UserAccount", e.getUserId()));
  }

  @Override
  public Optional<UserAccount> findByUsername(String username) {
    return selectOne(
        Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getUsername, username));
  }

  @Override
  public Optional<UserAccount> findByUserId(String userId) {
    return selectOne(
        Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getUserId, userId));
  }

  @Override
  public List<UserAccount> findAll(String tenantId) {
    return mapper
        .selectList(
            Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getTenantId, tenantId))
        .stream()
        .map(UserAccountEntity::toDomain)
        .toList();
  }

  @Override
  public UserAccount update(UserAccount account) {
    UserAccountEntity e = UserAccountEntity.fromDomain(account);
    e.setUpdatedAt(Instant.now());
    int affected = mapper.updateById(e);
    if (affected == 0) {
      throw new NotFoundException("UserAccount", account.userId());
    }
    return findByUserId(account.userId()).orElseThrow();
  }

  @Override
  public boolean existsByUsername(String username) {
    Long count =
        mapper.selectCount(
            Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getUsername, username));
    return count != null && count > 0;
  }

  private Optional<UserAccount> selectOne(LambdaQueryWrapper<UserAccountEntity> wrapper) {
    return Optional.ofNullable(mapper.selectOne(wrapper)).map(UserAccountEntity::toDomain);
  }
}
