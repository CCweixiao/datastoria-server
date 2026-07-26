package io.datastoria.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Scans the single MyBatis-Plus mapper package shared by SQLite and MySQL. Both profiles load the
 * same mapper XML resources (see the {@code mybatis-plus.mapper-locations} property); there is
 * exactly one mapper set for both databases. Domain {@code Repository} interfaces remain
 * implemented by adapters under {@code io.datastoria.server.persistence.repository}, so services
 * keep depending on the unchanged domain contracts.
 */
@Configuration
@MapperScan("io.datastoria.server.persistence.mapper")
public class MyBatisPlusConfig {}
