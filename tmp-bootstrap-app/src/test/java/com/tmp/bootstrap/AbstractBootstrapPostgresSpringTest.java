package com.tmp.bootstrap;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL Testcontainers wiring for bootstrap Spring tests.
 * H2 cannot apply Security V4 {@code lower(login)} unique index.
 *
 * <p>Container is started once for the JVM (not via {@code @Container}) so it survives
 * Surefire's multi-class lifecycle and delayed Ryuk cleanup between test classes.
 */
abstract class AbstractBootstrapPostgresSpringTest {

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Higher precedence than empty TMP_SECURITY_* env vars that would otherwise
        // override application-test.yml via relaxed binding.
        registry.add("tmp.security.bootstrap.admin-login", () -> "admin");
        registry.add("tmp.security.bootstrap.admin-display-name", () -> "Administrator");
        registry.add("tmp.security.bootstrap.admin-password", () -> "test-admin-password");
    }
}