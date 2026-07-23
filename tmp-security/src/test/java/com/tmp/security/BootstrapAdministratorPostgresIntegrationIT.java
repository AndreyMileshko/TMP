package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.security.application.BootstrapAdministratorApplicationService;
import com.tmp.security.domain.AuditOperation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = BootstrapAdministratorPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class BootstrapAdministratorPostgresIntegrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private BootstrapAdministratorApplicationService bootstrap;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentBootstrapCreatesExactlyOneAdminRoleAssignmentAndAudit() throws Exception {
        jdbcTemplate.update("DELETE FROM security.user_roles");
        jdbcTemplate.update("DELETE FROM security.role_permissions");
        jdbcTemplate.update("DELETE FROM security.security_audit_events");
        jdbcTemplate.update("DELETE FROM security.users");
        jdbcTemplate.update("DELETE FROM security.roles");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await(30, TimeUnit.SECONDS);
                    bootstrap.ensureBootstrapAdministrator();
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertEquals(true, done.await(60, TimeUnit.SECONDS));
        pool.shutdownNow();
        if (failure.get() != null) {
            throw new AssertionError("concurrent bootstrap failed", failure.get());
        }

        Long users = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM security.users", Long.class);
        Long roles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.roles WHERE lower(name) = lower(?)",
                Long.class,
                BootstrapAdministratorApplicationService.SECURITY_ADMINISTRATOR_ROLE_NAME);
        Long assignments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM security.user_roles", Long.class);
        Long audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.security_audit_events WHERE operation = ?",
                Long.class,
                AuditOperation.USER_CREATED.name());
        assertEquals(1L, users);
        assertEquals(1L, roles);
        assertEquals(1L, assignments);
        assertEquals(1L, audits);
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        SecurityAutoConfiguration.class
    })
    static class TestApplication {
    }
}
