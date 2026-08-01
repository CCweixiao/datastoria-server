package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.UserAccountEntity;

/**
 * Mapper for {@code ds_user_account}. Lookups are expressed with {@code LambdaQueryWrapper} in the
 * adapter, so this interface only inherits MyBatis-Plus CRUD.
 */
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {}
