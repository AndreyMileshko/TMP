package com.tmp.order.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.ViewDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderManagementCapabilityNavigationTest {

    @Test
    void navigationContributionIsRegisteredWithOrderViewPermission() {
        OrderManagementCapability capability = new OrderManagementCapability();
        List<NavigationContribution> navigation = capability.descriptor().navigationContributions();
        assertEquals(1, navigation.size());
        NavigationContribution entry = navigation.get(0);
        assertEquals(OrderManagementCapability.NAV_ORDERS, entry.navigationId());
        assertEquals("Заказы", entry.displayName());
        assertEquals(OrderManagementCapability.VIEW_ORDERS, entry.viewId());

        List<CommandDescriptor> commands = capability.descriptor().commands();
        assertEquals(1, commands.size());
        assertEquals(OrderManagementCapability.NAV_ORDERS, commands.get(0).commandId());
        assertEquals(
                List.of(OrderManagementPermissions.ORDER_VIEW.value()),
                commands.get(0).requiredPermissionIds());

        List<ViewDescriptor> views = capability.descriptor().views();
        assertEquals(1, views.size());
        assertEquals(OrderManagementCapability.VIEW_ORDERS, views.get(0).viewId());
        assertEquals(OrderManagementCapability.NAV_ORDERS, views.get(0).navigationTargetId());
    }

    @Test
    void navigationIdsAreUnique() {
        OrderManagementCapability capability = new OrderManagementCapability();
        List<String> navigationIds = capability.descriptor().navigationContributions().stream()
                .map(NavigationContribution::navigationId)
                .toList();
        Set<String> unique = new HashSet<>(navigationIds);
        assertEquals(navigationIds.size(), unique.size());
        assertTrue(unique.contains(OrderManagementCapability.NAV_ORDERS));
    }
}
