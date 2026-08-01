package io.github.ccweixiao.datastoria.boot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Scans the single MyBatis-Plus mapper package used by MySQL in both profiles. Both profiles load
 * the same mapper XML resources (see the {@code mybatis-plus.mapper-locations} property); there is
 * exactly one mapper set for both databases. Domain {@code Repository} interfaces remain
 * implemented by adapters under {@code io.github.ccweixiao.datastoria.dao.persistence.repository},
 * so services keep depending on the unchanged domain contracts.
 */
@Configuration
@MapperScan("io.github.ccweixiao.datastoria.dao.persistence.mapper")
public class MyBatisPlusConfig {}
