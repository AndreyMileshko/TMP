package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PasswordSetupRequiredException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.InvalidActivationCodeException;
import com.tmp.security.api.Login;
import com.tmp.security.api.PasswordResetResult;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserCreationResult;
import com.tmp.security.application.BootstrapAdministratorApplicationService;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.persistence.JdbcSecurityAuditRepository;
import com.tmp.security.support.ControllableSecurityAuditRepository;
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
@SpringBootTest(classes = ActivationPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value",
            "tmp.security.activation.ttl=PT1H"
        })
class ActivationPostgresIntegrationIT {

    private static final char[] ADMIN_PASSWORD = "bootstrap-secret-value".toCharArray();
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);

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
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void createUserReturnsActivationCodeAndRequiresItForSetup() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserCreationResult created = userAdministrationService.createUser(
                Login.of("activate-me"), DisplayName.of("Activate Me"));
        assertTrue(created.activationCode().matches(".*-.*-.*"));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT activation_code_hash IS NOT NULL FROM security.users WHERE lower(login) = lower(?)",
                Boolean.class,
                "activate-me"));
        authenticationService.logout();

        assertThrows(
                InvalidActivationCodeException.class,
                () -> authenticationService.completePasswordSetup(
                        Login.of("activate-me"),
                        "AAAA-BBBB-CCCC",
                        "user-secret-1".toCharArray(),
                        "user-secret-1".toCharArray()));

        authenticationService.completePasswordSetup(
                Login.of("activate-me"),
                created.activationCode(),
                "user-secret-1".toCharArray(),
                "user-secret-1".toCharArray());
        assertTrue(authenticationService.isAuthenticated());

        authenticationService.logout();
        assertThrows(
                InvalidActivationCodeException.class,
                () -> authenticationService.completePasswordSetup(
                        Login.of("activate-me"),
                        created.activationCode(),
                        "user-secret-2".toCharArray(),
                        "user-secret-2".toCharArray()));

        authenticationService.login(Login.of("activate-me"), "user-secret-1".toCharArray());
        assertTrue(authenticationService.isAuthenticated());
    }

    @Test
    void resetIssuesNewCodeAndInvalidatesOldPassword() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserCreationResult created = userAdministrationService.createUser(
                Login.of("reset-me"), DisplayName.of("Reset Me"));
        authenticationService.logout();
        authenticationService.completePasswordSetup(
                Login.of("reset-me"),
                created.activationCode(),
                "first-secret".toCharArray(),
                "first-secret".toCharArray());
        authenticationService.logout();

        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        PasswordResetResult reset = userAdministrationService.requestPasswordReset(created.user().id());
        authenticationService.logout();

        assertThrows(
                PasswordSetupRequiredException.class,
                () -> authenticationService.login(Login.of("reset-me"), "first-secret".toCharArray()));

        assertThrows(
                InvalidActivationCodeException.class,
                () -> authenticationService.completePasswordSetup(
                        Login.of("reset-me"),
                        created.activationCode(),
                        "second-secret".toCharArray(),
                        "second-secret".toCharArray()));

        authenticationService.completePasswordSetup(
                Login.of("reset-me"),
                reset.activationCode(),
                "second-secret".toCharArray(),
                "second-secret".toCharArray());
        authenticationService.logout();
        authenticationService.login(Login.of("reset-me"), "second-secret".toCharArray());
        assertTrue(authenticationService.isAuthenticated());
    }

    @Test
    void securityAdministratorRoleReceivesUsersViewOnExistingDatabase() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertTrue(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));
        var role = roleRepository.findAll().stream()
                .filter(r -> BootstrapAdministratorApplicationService.SECURITY_ADMINISTRATOR_ROLE_NAME
                        .equals(r.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(role.permissions().contains(SecurityPermissions.USERS_VIEW));
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

        @Bean
        @Primary
        Clock testClock() {
            return FIXED_CLOCK;
        }
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
