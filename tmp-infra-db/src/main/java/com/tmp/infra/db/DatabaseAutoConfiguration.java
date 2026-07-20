package com.tmp.infra.db;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Marker auto-configuration for TMP database infrastructure.
 * Datasource and Flyway are activated by Spring Boot only when
 * {@code spring.datasource.url} is provided by an active profile.
 */
@AutoConfiguration
public class DatabaseAutoConfiguration {
}
