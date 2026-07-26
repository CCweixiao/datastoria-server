package io.datastoria.server.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.RcaTemplateEntity;

/** Mapper for {@code ds_rca_template}. */
public interface RcaTemplateMapper extends BaseMapper<RcaTemplateEntity> {

  long countByKey(@Param("key") String key);

  int insertTemplate(RcaTemplateEntity entity);

  List<RcaTemplateEntity> findEnabledSources();

  RcaTemplateEntity findEnabledByKey(@Param("key") String key);
}
