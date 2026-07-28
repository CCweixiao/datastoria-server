package io.github.ccweixiao.datastoria.boot.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.dao.persistence.entity.RcaTemplateEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.RcaTemplateMapper;

@Component
public class RcaTemplateBootstrap implements ApplicationRunner {

  private final RcaTemplateMapper mapper;

  public RcaTemplateBootstrap(RcaTemplateMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (mapper.countByKey("high_part_count") > 0) {
      return;
    }
    String source =
        new ClassPathResource("rca/high-part-count.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
    long now = System.currentTimeMillis();
    RcaTemplateEntity entity = new RcaTemplateEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setTemplateKey("high_part_count");
    entity.setSourceYaml(source);
    entity.setEnabled(true);
    entity.setRevision(1L);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    mapper.insertTemplate(entity);
  }
}
