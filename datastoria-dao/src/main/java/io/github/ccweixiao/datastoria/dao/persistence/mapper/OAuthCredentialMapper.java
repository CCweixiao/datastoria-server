package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.OAuthCredentialEntity;

/** Mapper for {@code ds_oauth_credential}; the CAS update lives in the adapter. */
public interface OAuthCredentialMapper extends BaseMapper<OAuthCredentialEntity> {

  OAuthCredentialEntity findByOwner(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("providerKey") String providerKey);

  int insertCredential(OAuthCredentialEntity entity);

  int updateCas(
      @Param("entity") OAuthCredentialEntity entity,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);
}
