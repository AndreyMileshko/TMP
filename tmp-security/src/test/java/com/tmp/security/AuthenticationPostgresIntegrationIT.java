package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserAdministrationService;
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
    private AuthorizationService authorizationService;

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private ControllableSecurityAuditRepository controllableAudit;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSession() {
        try {
            authenticationService.logout();
        } catch (RuntimeException ignored) {
            // Controllable audit may still be armed from a prior failed logout assertion.
            assertFalse(authenticationService.isAuthenticated());
        }
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

    @Test
    void logoutClearsSessionEvenWhenAuditFails() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertTrue(authenticationService.isAuthenticated());
        controllableAudit.failNextAppend();
        assertThrows(IllegalStateException.class, authenticationService::logout);
        assertFalse(authenticationService.isAuthenticated());
        assertThrows(
                AccessDeniedException.class,
                () -> authorizationService.requirePermission(SecurityPermissions.USERS_VIEW));
    }

    @Test
    void failedLoginWithActiveSessionLeavesNoSession() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertTrue(authenticationService.isAuthenticated());

        assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(Login.of("admin"), "wrong-password".toCharArray()));
        assertFalse(authenticationService.isAuthenticated());

        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(Login.of("unknown-login"), "x".toCharArray()));
        assertFalse(authenticationService.isAuthenticated());

        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        var deleted = userAdministrationService.createUser(
                Login.of("to-delete-login"), DisplayName.of("To Delete"), "delete-secret".toCharArray());
        userAdministrationService.deleteUser(deleted.id());
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertThrows(
                AuthenticationFailedException.class,
                () -> authenticationService.login(Login.of("to-delete-login"), "delete-secret".toCharArray()));
        assertFalse(authenticationService.isAuthenticated());
    }

    @Test
    void successfulLoginReplacesPreviousSession() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        var other = userAdministrationService.createUser(
                Login.of("other-login"), DisplayName.of("Other"), "other-secret".toCharArray());
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        authenticationService.login(Login.of("other-login"), "other-secret".toCharArray());
        assertTrue(authenticationService.isAuthenticated());
        assertEquals(other.login().value(), authenticationService.currentSession().orElseThrow().login().value());
        assertTrue(auditDescriptionsAvoidSecrets());
    }

    private boolean auditDescriptionsAvoidSecrets() {
        Long bad = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM security.security_audit_events
                WHERE safe_description ILIKE '%bootstrap-secret%'
                   OR safe_description ILIKE '%other-secret%'
                   OR safe_description ILIKE '%$2a$%'
                """,
                Long.class);
        return bad != null && bad == 0L;
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
