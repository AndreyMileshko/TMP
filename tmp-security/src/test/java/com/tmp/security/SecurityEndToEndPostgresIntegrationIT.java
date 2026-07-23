package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.persistence.JdbcSecurityAuditRepository;
import com.tmp.security.support.ControllableSecurityAuditRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = SecurityEndToEndPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class SecurityEndToEndPostgresIntegrationIT {

    private static final char[] ADMIN_PASSWORD = "bootstrap-secret-value".toCharArray();
    private static final char[] OPERATOR_PASSWORD = "operator-secret-value".toCharArray();

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
    private RoleAdministrationService roleAdministrationService;

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ControllableSecurityAuditRepository controllableAudit;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void bootstrapAdminLoginCreatesUserRoleAssignmentAndEffectivePermissions() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        assertTrue(authenticationService.isAuthenticated());
        assertTrue(authorizationService.hasPermission(SecurityPermissions.USERS_CREATE));

        UserSummary operator = userAdministrationService.createUser(
                Login.of("operator"), DisplayName.of("Operator"), OPERATOR_PASSWORD.clone());
        RoleSummary role = roleAdministrationService.createRole("Operators", "Limited operators");
        roleAdministrationService.grantPermissionToRole(role.id(), SecurityPermissions.USERS_VIEW);
        roleAdministrationService.grantPermissionToRole(role.id(), SecurityPermissions.ROLES_VIEW);
        roleAdministrationService.assignRole(operator.id(), role.id());
        roleAdministrationService.grantIndividualPermission(operator.id(), SecurityPermissions.AUDIT_VIEW);

        authenticationService.logout();
        authenticationService.login(Login.of("operator"), OPERATOR_PASSWORD.clone());

        Set<PermissionId> effective = authorizationService.effectivePermissions();
        assertEquals(
                Set.of(
                        SecurityPermissions.USERS_VIEW,
                        SecurityPermissions.ROLES_VIEW,
                        SecurityPermissions.AUDIT_VIEW),
                effective);
    }

    @Test
    void mutationAndAuditRollBackTogetherWhenAuditAppendFails() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        RoleSummary role = roleAdministrationService.createRole("RollbackRole", "rollback");
        long permissionsBefore = countRolePermissions(role.id().value().toString());
        long auditBefore = countAuditEvents();

        controllableAudit.failNextAppend();
        assertThrows(
                IllegalStateException.class,
                () -> roleAdministrationService.grantPermissionToRole(
                        role.id(), SecurityPermissions.USERS_DELETE));

        assertEquals(permissionsBefore, countRolePermissions(role.id().value().toString()));
        assertEquals(auditBefore, countAuditEvents());
        assertFalse(roleRepository
                .findById(role.id())
                .orElseThrow()
                .permissions()
                .contains(SecurityPermissions.USERS_DELETE));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void inactiveCapabilityPermissionIsDeniedEvenIfRoleStillListsIt() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        UserSummary viewer = userAdministrationService.createUser(
                Login.of("auditviewer"), DisplayName.of("Audit Viewer"), "viewer-secret".toCharArray());
        RoleSummary role = roleAdministrationService.createRole("AuditReaders", "readers");
        roleAdministrationService.grantPermissionToRole(role.id(), SecurityPermissions.AUDIT_VIEW);
        roleAdministrationService.assignRole(viewer.id(), role.id());

        authenticationService.logout();
        authenticationService.login(Login.of("auditviewer"), "viewer-secret".toCharArray());
        assertTrue(authorizationService.hasPermission(SecurityPermissions.AUDIT_VIEW));

        capabilityEngine.deactivate(SecurityAdministrationCapability.ID);
        assertFalse(authorizationService.hasPermission(SecurityPermissions.AUDIT_VIEW));
        assertThrows(
                AccessDeniedException.class,
                () -> authorizationService.requirePermission(SecurityPermissions.AUDIT_VIEW));
    }

    private long countRolePermissions(String roleId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.role_permissions WHERE role_id = ?::uuid",
                Long.class,
                roleId);
        return count == null ? 0L : count;
    }

    private long countAuditEvents() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.security_audit_events", Long.class);
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
