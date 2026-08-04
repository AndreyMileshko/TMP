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
        assertEquals(11, documents.size());
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
    void capabilityDescriptorExposesCatalogAndOrderListNavigation() {
        OrderManagementCapability capability = new OrderManagementCapability();
        assertEquals(OrderManagementCapability.ID, capability.descriptor().id());
        assertEquals(13, capability.descriptor().permissions().size());
        assertEquals(1, capability.descriptor().navigationContributions().size());
        assertEquals(1, capability.descriptor().views().size());
        assertEquals(1, capability.descriptor().commands().size());
        assertEquals(
                OrderManagementCapability.NAV_ORDERS,
                capability.descriptor().navigationContributions().get(0).navigationId());
        assertEquals(
                "Заказы",
                capability.descriptor().navigationContributions().get(0).displayName());
        assertEquals(
                OrderManagementCapability.VIEW_ORDERS,
                capability.descriptor().navigationContributions().get(0).viewId());
    }

    @Test
    void allThirteenDisplayNamesAreExactRussianAndIdsUnchanged() {
        var byId = OrderManagementPermissionCatalog.all().stream()
                .collect(Collectors.toMap(
                        PermissionDescriptor::permissionId, PermissionDescriptor::displayName));

        assertEquals("Просмотр заказов", byId.get("order.order.view"));
        assertEquals("Создание заказов", byId.get("order.order.create"));
        assertEquals("Изменение заказов", byId.get("order.order.edit"));
        assertEquals("Утверждение заказов", byId.get("order.order.approve"));
        assertEquals("Отмена заказов", byId.get("order.order.cancel"));
        assertEquals("Просмотр позиций заказа", byId.get("order.item.view"));
        assertEquals("Создание позиций заказа", byId.get("order.item.create"));
        assertEquals("Изменение позиций заказа", byId.get("order.item.edit"));
        assertEquals("Утверждение позиций заказа", byId.get("order.item.approve"));
        assertEquals("Отмена позиций заказа", byId.get("order.item.cancel"));
        assertEquals("Создание редакций позиции заказа", byId.get("order.revision.create"));
        assertEquals("Изменение редакций позиции заказа", byId.get("order.revision.edit"));
        assertEquals("Просмотр спецификаций позиции заказа", byId.get("order.specification.view"));

        assertEquals(
                OrderManagementPermissions.all().stream().map(PermissionId::value).collect(Collectors.toSet()),
                byId.keySet());
    }
}
