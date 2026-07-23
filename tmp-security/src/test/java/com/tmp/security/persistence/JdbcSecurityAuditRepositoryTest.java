package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
@SpringBootTest(classes = JdbcSecurityAuditRepositoryTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcSecurityAuditRepositoryTest {

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

    private JdbcSecurityAuditRepository repository;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        repository = new JdbcSecurityAuditRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.security_audit_events");
        jdbcTemplate.update("DELETE FROM security.users");
        actorId = new JdbcUserRepository(jdbcTemplate)
                .save(User.createActive(
                        UserId.generate(),
                        Login.of("admin"),
                        DisplayName.of("Admin"),
                        PasswordHash.of("$2a$10$hash"),
                        CLOCK))
                .id();
    }

    @Test
    void appendReadFilterAndPagination() {
        for (int i = 0; i < 5; i++) {
            repository.append(SecurityAuditEvent.record(
                    AuditEventId.generate(),
                    Instant.parse("2026-07-23T0" + (3 + i) + ":00:00Z"),
                    actorId,
                    "admin",
                    i % 2 == 0 ? AuditOperation.LOGIN_SUCCESS : AuditOperation.USER_CREATED,
                    "USER",
                    "t" + i,
                    "safe " + i,
                    AuditResult.SUCCESS));
        }
        assertEquals(5, repository.count(new AuditQueryFilter(null, null, null, null)));
        assertEquals(3, repository.count(new AuditQueryFilter(null, null, null, AuditOperation.LOGIN_SUCCESS)));
        assertEquals(5, repository.count(new AuditQueryFilter(null, null, actorId, null)));

        List<SecurityAuditEvent> page0 = repository.findPage(
                new AuditQueryFilter(null, null, null, null), 0, 2);
        List<SecurityAuditEvent> page1 = repository.findPage(
                new AuditQueryFilter(null, null, null, null), 1, 2);
        List<SecurityAuditEvent> page2 = repository.findPage(
                new AuditQueryFilter(null, null, null, null), 2, 2);
        assertEquals(2, page0.size());
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
        assertEquals("t4", page0.get(0).targetIdentifier());
        assertEquals("t0", page2.get(0).targetIdentifier());
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
