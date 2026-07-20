package com.tmp.infra.db;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresTestcontainersSupport {

    private PostgresTestcontainersSupport() {
    }

    public static PostgreSQLContainer<?> createPostgreSqlContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
