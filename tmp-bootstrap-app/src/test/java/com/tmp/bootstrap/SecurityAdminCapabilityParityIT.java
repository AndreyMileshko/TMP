package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SecurityPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Security Administrator bootstrap ensure parity: Security + Warehouse + Order + Production.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityAdminCapabilityParityIT extends AbstractBootstrapPostgresSpringTest {

    private static final char[] ADMIN_PASSWORD = "test-admin-password".toCharArray();

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private AuthorizationService authorizationService;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void securityAdministratorHasOrderAndProductionCapabilitiesAfterEnsure() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());

        assertTrue(authorizationService.hasPermission(SecurityPermissions.USERS_VIEW));
        assertTrue(authorizationService.hasPermission(OrderManagementPermissions.ORDER_VIEW));
        assertTrue(authorizationService.hasPermission(OrderManagementPermissions.ORDER_CREATE));
        assertTrue(authorizationService.hasPermission(ProductionPermissions.PRODUCTION_VIEW));
        assertTrue(authorizationService.hasPermission(ProductionPermissions.PRODUCTION_RELEASE));
    }
}
