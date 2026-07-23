package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.PermissionOverrideDecision;
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

@Testcontainers
@SpringBootTest(classes = JdbcPermissionOverrideRepositoryTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcPermissionOverrideRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");

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

    private JdbcPermissionOverrideRepository repository;
    private UserId userId;

    @BeforeEach
    void setUp() {
        repository = new JdbcPermissionOverrideRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.user_permission_overrides");
        jdbcTemplate.update("DELETE FROM security.users");
        jdbcTemplate.update("DELETE FROM security.permission_definitions");
        new JdbcPermissionDefinitionRepository(jdbcTemplate)
                .save(PermissionDefinition.register(VIEW, "test.capability", "View", "", CLOCK));
        userId = new JdbcUserRepository(jdbcTemplate)
                .save(User.createActive(
                        UserId.generate(),
                        Login.of("u1"),
                        DisplayName.of("U"),
                        PasswordHash.of("$2a$10$hash"),
                        CLOCK))
                .id();
    }

    @Test
    void saveFlipAndRemove() {
        IndividualPermissionOverride grant = repository.save(
                IndividualPermissionOverride.of(userId, VIEW, PermissionOverrideDecision.GRANT, CLOCK));
        IndividualPermissionOverride revoked = repository.save(
                grant.withDecision(PermissionOverrideDecision.REVOKE, CLOCK));
        assertEquals(PermissionOverrideDecision.REVOKE, revoked.decision());
        assertEquals(1L, revoked.version());
        assertEquals(1, repository.findByUser(userId).size());
        repository.remove(userId, VIEW);
        assertTrue(repository.findByUserAndPermission(userId, VIEW).isEmpty());
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
