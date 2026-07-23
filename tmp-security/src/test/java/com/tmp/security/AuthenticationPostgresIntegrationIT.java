package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.persistence.JdbcSecurityAuditRepository;
import com.tmp.security.support.ControllableSecurityAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = AuthenticationPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class AuthenticationPostgresIntegrationIT {

    private static final char[] ADMIN_PASSWORD = "bootstrap-secret-value".toCharArray();

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
    private AuthenticationService authenticationService;

    @Autowired
    private ControllableSecurityAuditRepository controllableAudit;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void failedLoginAuditIsCommittedDespiteAuthenticationException() {
        long failuresBefore = countAudit(AuditOperation.LOGIN_FAILURE.name());
        assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(Login.of("admin"), "wrong-password".toCharArray()));
        assertFalse(authenticationService.isAuthenticated());
        assertEquals(failuresBefore + 1, countAudit(AuditOperation.LOGIN_FAILURE.name()));
    }

    @Test
    void unknownLoginFailureAuditIsCommitted() {
        long failuresBefore = countAudit(AuditOperation.LOGIN_FAILURE.name());
        assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(Login.of("nobody"), "x".toCharArray()));
        assertFalse(authenticationService.isAuthenticated());
        assertEquals(failuresBefore + 1, countAudit(AuditOperation.LOGIN_FAILURE.name()));
    }

    @Test
    void auditFailureOnSuccessfulCredentialsLeavesNoSession() {
        long successBefore = countAudit(AuditOperation.LOGIN_SUCCESS.name());
        controllableAudit.failNextAppend();
        assertThrows(
                IllegalStateException.class,
                () -> authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone()));
        assertFalse(authenticationService.isAuthenticated());
        assertEquals(successBefore, countAudit(AuditOperation.LOGIN_SUCCESS.name()));
    }

    @Test
    void successfulLoginPersistsAuditAndOpensSession() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertTrue(authenticationService.isAuthenticated());
        assertTrue(countAudit(AuditOperation.LOGIN_SUCCESS.name()) >= 1L);
    }

    private long countAudit(String operation) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.security_audit_events WHERE operation = ?",
                Long.class,
                operation);
        return count == null ? 0L : count;
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        AuditProbeConfiguration.class
    })
    static class TestApplication {
    }

    static class AuditProbeConfiguration {

        @Bean
        @Primary
        ControllableSecurityAuditRepository controllableSecurityAuditRepository(JdbcTemplate jdbcTemplate) {
            SecurityAuditRepository jdbc = new JdbcSecurityAuditRepository(jdbcTemplate);
            return new ControllableSecurityAuditRepository(jdbc);
        }
    }
}
