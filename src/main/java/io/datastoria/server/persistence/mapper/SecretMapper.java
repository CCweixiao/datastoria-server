package io.datastoria.server.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.SecretEntity;

/**
 * Mapper for {@code ds_secret}. {@code findMaskedById} deliberately omits the BLOB columns so they
 * are not materialised; {@code findEncryptedById} returns the full row including the cipher text.
 */
public interface SecretMapper extends BaseMapper<SecretEntity> {

  int insertSecret(SecretEntity entity);

  SecretEntity findEncryptedById(@Param("id") String id, @Param("tenantId") String tenantId);

  SecretEntity findMaskedById(@Param("id") String id, @Param("tenantId") String tenantId);

  int softDelete(
      @Param("id") String id, @Param("tenantId") String tenantId, @Param("now") Instant now);
}
