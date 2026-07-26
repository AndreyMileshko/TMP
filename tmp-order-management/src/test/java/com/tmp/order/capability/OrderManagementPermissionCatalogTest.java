package com.tmp.order.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.order.application.document.OrderBusinessDocumentCatalog;
import com.tmp.order.application.document.OrderDocumentTypeDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OrderManagementPermissionCatalogTest {

    @Test
    void registersExactlyThirteenCapabilitiesWithoutDuplicates() {
        List<PermissionId> permissions = OrderManagementPermissions.all();
        assertEquals(13, permissions.size());
        assertEquals(13, new HashSet<>(permissions).size());

        List<PermissionDescriptor> descriptors = OrderManagementPermissionCatalog.all();
        assertEquals(13, descriptors.size());
        assertEquals(
                13,
                descriptors.stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet())
                        .size());
    }

    @Test
    void allPermissionIdsAreValidThreeSegmentCodes() {
        for (PermissionId permissionId : OrderManagementPermissions.all()) {
            assertEquals(permissionId, PermissionId.of(permissionId.value()));
            assertEquals(3, permissionId.value().split("\\.").length);
        }
    }

    @Test
    void everyBusinessDocumentMapsToExistingCapability() {
        List<OrderDocumentTypeDescriptor> documents = OrderBusinessDocumentCatalog.all();
        assertEquals(10, documents.size());
        for (OrderDocumentTypeDescriptor document : documents) {
            assertTrue(
                    OrderManagementPermissionCatalog.containsCode(document.requiredCapability()),
                    () -> "Missing capability for document "
                            + document.documentTypeCode()
                            + ": "
                            + document.requiredCapability());
        }
    }

    @Test
    void viewCapabilitiesArePresentForQueryApi() {
        Set<PermissionId> views = OrderManagementPermissions.viewCapabilities();
        assertEquals(
                Set.of(
                        OrderManagementPermissions.ORDER_VIEW,
                        OrderManagementPermissions.ITEM_VIEW,
                        OrderManagementPermissions.SPECIFICATION_VIEW),
                views);
        for (PermissionId view : views) {
            assertTrue(OrderManagementPermissionCatalog.contains(view));
        }
    }

    @Test
    void unknownCapabilitiesAreAbsent() {
        assertFalse(OrderManagementPermissionCatalog.containsCode("order.order.delete"));
        assertFalse(OrderManagementPermissionCatalog.containsCode("order.production.view"));
        assertFalse(OrderManagementPermissionCatalog.containsCode("warehouse.stock.view"));
        assertFalse(OrderManagementPermissionCatalog.containsCode("security.users.view"));
    }

    @Test
    void capabilityDescriptorExposesCatalogWithoutNavigation() {
        OrderManagementCapability capability = new OrderManagementCapability();
        assertEquals(OrderManagementCapability.ID, capability.descriptor().id());
        assertEquals(13, capability.descriptor().permissions().size());
        assertTrue(capability.descriptor().navigationContributions().isEmpty());
        assertTrue(capability.descriptor().views().isEmpty());
    }
}
