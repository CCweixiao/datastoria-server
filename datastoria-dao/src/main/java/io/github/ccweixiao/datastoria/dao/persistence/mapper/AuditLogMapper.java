package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.AuditLogEntity;

/** Mapper for {@code ds_audit_log}. The auto-increment id is retrieved via JDBC generated keys. */
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

  int insertAudit(AuditLogEntity entity);
}
