package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
@SpringBootTest(classes = JdbcUserUiPreferenceServiceTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcUserUiPreferenceServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-03T08:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

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
    private JdbcUserUiPreferenceService preferences;

    @BeforeEach
    void setUp() {
        users = new JdbcUserRepository(jdbcTemplate);
        preferences = new JdbcUserUiPreferenceService(jdbcTemplate, CLOCK);
        jdbcTemplate.update("DELETE FROM security.user_ui_preferences");
        jdbcTemplate.update("DELETE FROM security.security_audit_events");
        jdbcTemplate.update("DELETE FROM security.user_permission_overrides");
        jdbcTemplate.update("DELETE FROM security.user_roles");
        jdbcTemplate.update("DELETE FROM security.users");
    }

    @Test
    void roundTripPerUser() {
        UserId userA = saveUser("pref-a", "Pref A");
        UserId userB = saveUser("pref-b", "Pref B");

        preferences.save(userA, "ui.orders.list.v1", "filters", 1, "statuses=EDITING");
        preferences.save(userB, "ui.orders.list.v1", "filters", 1, "statuses=COMPLETED");

        assertEquals(
                Optional.of("statuses=EDITING"),
                preferences.load(userA, "ui.orders.list.v1", "filters"));
        assertEquals(
                Optional.of("statuses=COMPLETED"),
                preferences.load(userB, "ui.orders.list.v1", "filters"));
    }

    @Test
    void overwriteUpdatesValue() {
        UserId userId = saveUser("pref-c", "Pref C");
        preferences.save(userId, "ui.orders.list.v1", "filters", 1, "first");
        preferences.save(userId, "ui.orders.list.v1", "filters", 1, "second");
        assertEquals(Optional.of("second"), preferences.load(userId, "ui.orders.list.v1", "filters"));
    }

    @Test
    void missingPreferenceIsEmpty() {
        UserId userId = saveUser("pref-d", "Pref D");
        assertTrue(preferences.load(userId, "ui.orders.list.v1", "filters").isEmpty());
    }

    private UserId saveUser(String login, String displayName) {
        return users.save(
                        User.createActive(
                                UserId.generate(),
                                Login.of(login),
                                DisplayName.of(displayName),
                                PasswordHash.of("$2a$10$abcdefghijklmnop"),
                                CLOCK))
                .id();
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
