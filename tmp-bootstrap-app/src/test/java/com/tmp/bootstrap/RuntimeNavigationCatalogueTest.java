package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.sample.SampleTechnicalCapability;
import com.tmp.order.capability.OrderManagementCapability;
import com.tmp.production.security.ProductionCapability;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.ui.shell.navigation.ShellNavEntry;
import com.tmp.ui.shell.navigation.ShellNavigationCatalogue;
import com.tmp.ui.shell.screen.main.MainWindowViewModel;
import com.tmp.warehouse.security.WarehouseCapability;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies production-style runtime wiring: diagnostic sample capabilities stay off the user
 * navigation catalogue while business capabilities remain registered.
 */
@SpringBootTest(properties = "tmp.capability.sample.diagnostic=false")
@ActiveProfiles("test")
class RuntimeNavigationCatalogueTest extends AbstractBootstrapPostgresSpringTest {

    private static final char[] ADMIN_PASSWORD = "test-admin-password".toCharArray();

    private static final Set<String> EXPECTED_BUSINESS_NAV_IDS =
            Set.of(
                    SecurityAdministrationCapability.NAV_USERS,
                    SecurityAdministrationCapability.NAV_ROLES,
                    SecurityAdministrationCapability.NAV_AUDIT,
                    OrderManagementCapability.NAV_ORDERS,
                    WarehouseCapability.NAV_WAREHOUSE,
                    ProductionCapability.NAV_PRODUCTION);

    private static final Set<String> ADMIN_VISIBLE_NAV_IDS =
            Set.of(
                    SecurityAdministrationCapability.NAV_USERS,
                    SecurityAdministrationCapability.NAV_ROLES,
                    SecurityAdministrationCapability.NAV_AUDIT);

    @Autowired
    private ShellNavigationCatalogue shellNavigationCatalogue;

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Autowired
    private MainWindowViewModel mainWindowViewModel;

    @Autowired
    private AuthenticationService authenticationService;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void runtimeNavigationExcludesSampleTechnicalButKeepsBusinessEntries() {
        Set<String> navigationIds =
                shellNavigationCatalogue.entries().stream()
                        .map(ShellNavEntry::navigationId)
                        .collect(Collectors.toSet());
        Set<String> displayNames =
                shellNavigationCatalogue.entries().stream()
                        .map(ShellNavEntry::displayName)
                        .collect(Collectors.toSet());

        assertFalse(navigationIds.contains("sample.technical.nav"));
        assertFalse(displayNames.contains("Sample technical"));
        assertTrue(EXPECTED_BUSINESS_NAV_IDS.stream().allMatch(navigationIds::contains));

        assertFalse(capabilityEngine.registeredCapabilities().stream()
                .anyMatch(descriptor -> SampleTechnicalCapability.ID.equals(descriptor.id())));
        assertTrue(capabilityEngine.registeredCapabilities().stream()
                .anyMatch(descriptor -> SecurityAdministrationCapability.ID.equals(descriptor.id())));
        assertTrue(capabilityEngine.registeredCapabilities().stream()
                .anyMatch(descriptor -> OrderManagementCapability.ID.equals(descriptor.id())));
        assertTrue(capabilityEngine.registeredCapabilities().stream()
                .anyMatch(descriptor -> WarehouseCapability.ID.equals(descriptor.id())));
        assertTrue(capabilityEngine.registeredCapabilities().stream()
                .anyMatch(descriptor -> ProductionCapability.ID.equals(descriptor.id())));

        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        mainWindowViewModel.refreshNavigation();
        Set<String> visibleNavigationIds =
                mainWindowViewModel.navigationItems().stream()
                        .map(item -> item.navigationId())
                        .collect(Collectors.toSet());
        assertFalse(visibleNavigationIds.contains("sample.technical.nav"));
        assertTrue(ADMIN_VISIBLE_NAV_IDS.stream().allMatch(visibleNavigationIds::contains));
        assertTrue(visibleNavigationIds.stream().allMatch(navigationIds::contains));
    }
}
