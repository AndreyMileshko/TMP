package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.DuplicateLoginException;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/**
 * Uses PostgreSQL Testcontainers because V4's unique index on {@code lower(login)}
 * is not supported by H2.
 */
@Testcontainers
@SpringBootTest(classes = JdbcUserRepositoryTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcUserRepositoryTest {

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

    private JdbcUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcUserRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.security_audit_events");
        jdbcTemplate.update("DELETE FROM security.user_permission_overrides");
        jdbcTemplate.update("DELETE FROM security.user_roles");
        jdbcTemplate.update("DELETE FROM security.users");
    }

    @Test
    void roundTrip() {
        User created = User.createActive(
                UserId.generate(),
                Login.of("Alice"),
                DisplayName.of("Alice Admin"),
                PasswordHash.of("$2a$10$abcdefghijklmnop"),
                CLOCK);
        User saved = repository.save(created);
        User loaded = repository.findById(saved.id()).orElseThrow();
        assertEquals(created.login(), loaded.login());
        assertEquals(created.displayName(), loaded.displayName());
        assertEquals(created.passwordHash(), loaded.passwordHash());
        assertTrue(loaded.isActive());
        assertTrue(repository.existsByLoginIgnoreCase(Login.of("alice")));
        assertEquals(loaded.id(), repository.findByLoginIgnoreCase(Login.of("ALICE")).orElseThrow().id());
    }

    @Test
    void optimisticLockConflict() {
        User created = repository.save(User.createActive(
                UserId.generate(),
                Login.of("bob"),
                DisplayName.of("Bob"),
                PasswordHash.of("$2a$10$hash"),
                CLOCK));
        User stale = User.rehydrate(
                created.id(),
                created.login(),
                DisplayName.of("Stale"),
                created.passwordHash(),
                created.status(),
                0L,
                created.createdAt(),
                created.updatedAt());
        repository.save(created.withDisplayName(DisplayName.of("Bob2"), CLOCK));
        assertThrows(OptimisticLockConflictException.class, () -> repository.save(stale));
    }

    @Test
    void caseInsensitiveDuplicateLoginRejected() {
        repository.save(User.createActive(
                UserId.generate(),
                Login.of("Admin"),
                DisplayName.of("A"),
                PasswordHash.of("$2a$10$hash"),
                CLOCK));
        assertThrows(DuplicateLoginException.class, () -> repository.save(User.createActive(
                UserId.generate(),
                Login.of("admin"),
                DisplayName.of("B"),
                PasswordHash.of("$2a$10$hash2"),
                CLOCK)));
        assertFalse(repository.existsByLoginIgnoreCase(Login.of("missing")));
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
