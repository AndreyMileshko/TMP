package com.tmp.warehouse.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WarehousePermissionCatalogTest {

    @Test
    void registersExactlyEightCapabilitiesWithoutDuplicates() {
        List<PermissionId> permissions = WarehousePermissions.all();
        assertEquals(8, permissions.size());
        assertEquals(8, new HashSet<>(permissions).size());

        List<PermissionDescriptor> descriptors = WarehousePermissionCatalog.all();
        assertEquals(8, descriptors.size());
        assertEquals(
                8,
                descriptors.stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet())
                        .size());
    }

    @Test
    void allPermissionIdsAreValidThreeSegmentCodes() {
        for (PermissionId permissionId : WarehousePermissions.all()) {
            assertEquals(permissionId, PermissionId.of(permissionId.value()));
            assertEquals(3, permissionId.value().split("\\.").length);
        }
    }

    @Test
    void logicalWarehouseCapabilitiesMapToExpectedCodes() {
        assertEquals("warehouse.stock.view", WarehousePermissions.WAREHOUSE_VIEW.value());
        assertEquals("warehouse.receipt.create", WarehousePermissions.WAREHOUSE_RECEIPT.value());
        assertEquals("warehouse.move.create", WarehousePermissions.WAREHOUSE_MOVE.value());
        assertEquals("warehouse.transfer.create", WarehousePermissions.WAREHOUSE_TRANSFER.value());
        assertEquals(
                "warehouse.reservation.create", WarehousePermissions.WAREHOUSE_RESERVATION.value());
        assertEquals(
                "warehouse.consumption.create", WarehousePermissions.WAREHOUSE_CONSUMPTION.value());
        assertEquals(
                "warehouse.adjustment.create", WarehousePermissions.WAREHOUSE_ADJUSTMENT.value());
        assertEquals("warehouse.inventory.create", WarehousePermissions.WAREHOUSE_INVENTORY.value());
    }

    @Test
    void unknownCapabilitiesAreAbsent() {
        assertFalse(WarehousePermissionCatalog.containsCode("warehouse.stock.delete"));
        assertFalse(WarehousePermissionCatalog.containsCode("order.order.view"));
        assertFalse(WarehousePermissionCatalog.containsCode("security.users.view"));
    }

    @Test
    void capabilityDescriptorExposesPermissionCatalogAndNavigation() {
        WarehouseCapability capability = new WarehouseCapability();
        assertEquals(WarehouseCapability.ID, capability.descriptor().id());
        assertEquals(8, capability.descriptor().permissions().size());
        assertEquals(1, capability.descriptor().navigationContributions().size());
        assertEquals(1, capability.descriptor().views().size());
        assertEquals(1, capability.descriptor().commands().size());
        assertEquals(
                WarehouseCapability.VIEW_WAREHOUSE,
                capability.descriptor().navigationContributions().get(0).viewId());
        assertEquals(
                WarehousePermissions.all().stream().map(PermissionId::value).collect(Collectors.toSet()),
                capability.descriptor().permissions().stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void displayNamesMatchSpecificationIntents() {
        var byId =
                WarehousePermissionCatalog.all().stream()
                        .collect(
                                Collectors.toMap(
                                        PermissionDescriptor::permissionId,
                                        PermissionDescriptor::displayName));

        assertEquals("Просмотр складов", byId.get("warehouse.stock.view"));
        assertEquals("Приёмка на склад", byId.get("warehouse.receipt.create"));
        assertEquals("Внутреннее перемещение", byId.get("warehouse.move.create"));
        assertEquals("Межскладское перемещение", byId.get("warehouse.transfer.create"));
        assertEquals("Информационное резервирование", byId.get("warehouse.reservation.create"));
        assertEquals("Списание материалов", byId.get("warehouse.consumption.create"));
        assertEquals("Корректировка остатков", byId.get("warehouse.adjustment.create"));
        assertEquals("Инвентаризация", byId.get("warehouse.inventory.create"));
    }
}
