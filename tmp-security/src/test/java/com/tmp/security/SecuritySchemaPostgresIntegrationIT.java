package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.DuplicateLoginException;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.api.RoleId;
import com.tmp.security.persistence.JdbcRoleRepository;
import com.tmp.security.persistence.JdbcUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = SecuritySchemaPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
class SecuritySchemaPostgresIntegrationIT {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

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
    private JdbcTemplate jdbcTemplate;

    private JdbcUserRepository users;
    private JdbcRoleRepository roles;

    @BeforeEach
    void setUp() {
        users = new JdbcUserRepository(jdbcTemplate);
        roles = new JdbcRoleRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.security_audit_events");
        jdbcTemplate.update("DELETE FROM security.user_permission_overrides");
        jdbcTemplate.update("DELETE FROM security.user_roles");
        jdbcTemplate.update("DELETE FROM security.role_permissions");
        jdbcTemplate.update("DELETE FROM security.roles");
        jdbcTemplate.update("DELETE FROM security.users");
        jdbcTemplate.update("DELETE FROM security.permission_definitions");
    }

    @Test
    void requiredTablesAndNoPlaintextPasswordColumn() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'security'
                ORDER BY table_name
                """,
                String.class);
        assertTrue(tables.containsAll(List.of(
                "users",
                "roles",
                "permission_definitions",
                "role_permissions",
                "user_roles",
                "user_permission_overrides",
                "security_audit_events")));

        List<String> passwordLike = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'security' AND column_name ILIKE '%password%'
                """,
                String.class);
        assertEquals(List.of("password_hash"), passwordLike);

        Boolean notNull = jdbcTemplate.queryForObject(
                """
                SELECT is_nullable = 'NO' FROM information_schema.columns
                WHERE table_schema = 'security' AND table_name = 'users' AND column_name = 'password_hash'
                """,
                Boolean.class);
        assertEquals(Boolean.TRUE, notNull);
    }

    @Test
    void caseInsensitiveUniqueLogin() {
        users.save(User.createActive(
                UserId.generate(),
                Login.of("Admin"),
                DisplayName.of("A"),
                PasswordHash.of("$2a$10$hash"),
                CLOCK));
        assertThrows(DuplicateLoginException.class, () -> users.save(User.createActive(
                UserId.generate(),
                Login.of("admin"),
                DisplayName.of("B"),
                PasswordHash.of("$2a$10$hash2"),
                CLOCK)));
    }

    @Test
    void concurrentDuplicateLoginExactlyOneSucceeds() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool.submit(() -> raceInsert(start, successes, failures, "RaceUser"));
            Future<?> f2 = pool.submit(() -> raceInsert(start, successes, failures, "raceuser"));
            start.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
    }

    private void raceInsert(
            CountDownLatch start, AtomicInteger successes, AtomicInteger failures, String login) {
        try {
            start.await(10, TimeUnit.SECONDS);
            users.save(User.createActive(
                    UserId.generate(),
                    Login.of(login),
                    DisplayName.of(login),
                    PasswordHash.of("$2a$10$race"),
                    CLOCK));
            successes.incrementAndGet();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failures.incrementAndGet();
        } catch (DuplicateLoginException ex) {
            failures.incrementAndGet();
        }
    }

    @Test
    void optimisticLockingUsersAndRoles() {
        User user = users.save(User.createActive(
                UserId.generate(),
                Login.of("lockuser"),
                DisplayName.of("L"),
                PasswordHash.of("$2a$10$hash"),
                CLOCK));
        users.save(user.withDisplayName(DisplayName.of("L2"), CLOCK));
        User staleUser = User.rehydrate(
                user.id(), user.login(), DisplayName.of("stale"), user.passwordHash(),
                user.status(), 0L, user.createdAt(), user.updatedAt());
        assertThrows(OptimisticLockConflictException.class, () -> users.save(staleUser));

        Role role = roles.save(Role.create(RoleId.generate(), "lockrole", "", CLOCK));
        roles.save(role.withName("lockrole2", CLOCK));
        Role staleRole = Role.rehydrate(
                role.id(), "stale", "", java.util.Set.of(), 0L, role.createdAt(), role.updatedAt());
        assertThrows(OptimisticLockConflictException.class, () -> roles.save(staleRole));
    }

    @Test
    void logicalDeletionKeepsRow() {
        User user = users.save(User.createActive(
                UserId.generate(),
                Login.of("todelete"),
                DisplayName.of("D"),
                PasswordHash.of("$2a$10$hash"),
                CLOCK));
        User deleted = users.save(user.deleted(CLOCK));
        User loaded = users.findById(deleted.id()).orElseThrow();
        assertEquals(UserStatus.DELETED, loaded.status());
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.users WHERE id = ?", Long.class, deleted.id().value());
        assertEquals(1L, count);
    }

    @SpringBootApplication(
            excludeName = {
                "com.tmp.security.SecurityAutoConfiguration",
                "com.tmp.capability.CapabilityEngineAutoConfiguration",
                "com.tmp.document.DocumentEngineAutoConfiguration",
                "com.tmp.core.PlatformCoreAutoConfiguration"
            })
    @Import(com.tmp.infra.db.DatabaseAutoConfiguration.class)
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(javax.sql.DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
