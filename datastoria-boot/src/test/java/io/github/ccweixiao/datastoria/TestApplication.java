package io.github.ccweixiao.datastoria;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

import io.github.ccweixiao.datastoria.boot.DatastoriaServerApplication;

/**
 * Test-only root configuration that preserves automatic Spring Boot configuration discovery for
 * tests located in the packages of the individual production modules.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(DatastoriaServerApplication.class)
public class TestApplication {}
