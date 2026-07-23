package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.RoleAssignment;
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
@SpringBootTest(classes = JdbcRoleAssignmentRepositoryTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcRoleAssignmentRepositoryTest {

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

    private JdbcRoleAssignmentRepository assignments;
    private UserId userId;
    private RoleId roleId;

    @BeforeEach
    void setUp() {
        assignments = new JdbcRoleAssignmentRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.user_roles");
        jdbcTemplate.update("DELETE FROM security.role_permissions");
        jdbcTemplate.update("DELETE FROM security.roles");
        jdbcTemplate.update("DELETE FROM security.users");
        jdbcTemplate.update("DELETE FROM security.permission_definitions");

        JdbcUserRepository users = new JdbcUserRepository(jdbcTemplate);
        JdbcRoleRepository roles = new JdbcRoleRepository(jdbcTemplate);
        JdbcPermissionDefinitionRepository permissions = new JdbcPermissionDefinitionRepository(jdbcTemplate);
        PermissionId view = PermissionId.of("security.users.view");
        permissions.save(PermissionDefinition.register(view, "test.capability", "View", "", CLOCK));
        userId = users.save(User.createActive(
                        UserId.generate(),
                        Login.of("u1"),
                        DisplayName.of("U"),
                        PasswordHash.of("$2a$10$hash"),
                        CLOCK))
                .id();
        roleId = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK).grantPermission(view, CLOCK)).id();
    }

    @Test
    void assignIsIdempotentAndCountable() {
        RoleAssignment assignment = RoleAssignment.of(userId, roleId, CLOCK.instant());
        assignments.assign(assignment);
        assignments.assign(assignment);
        assertEquals(1, assignments.findRoleIdsForUser(userId).size());
        assertEquals(1L, assignments.countUsersForRole(roleId));
        assertEquals(1, assignments.findUserIdsForRole(roleId).size());
        assignments.revoke(userId, roleId);
        assertEquals(0L, assignments.countUsersForRole(roleId));
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
