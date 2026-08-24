package com.tmp.production.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductionPermissionCatalogTest {

    @Test
    void registersExactlySevenDescriptorsWithoutDuplicates() {
        List<PermissionDescriptor> descriptors = ProductionPermissionCatalog.all();
        assertEquals(7, descriptors.size());
        assertEquals(
                7,
                descriptors.stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet())
                        .size());
        assertEquals(
                ProductionPermissions.all().stream().map(PermissionId::value).collect(Collectors.toSet()),
                descriptors.stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void descriptorLabelsAndDescriptionsAreNonBlank() {
        for (PermissionDescriptor descriptor : ProductionPermissionCatalog.all()) {
            assertFalse(descriptor.displayName().isBlank());
            assertFalse(descriptor.description().isBlank());
        }
    }

    @Test
    void catalogDoesNotDeclareWarehouseOwnedPermissions() {
        for (PermissionDescriptor descriptor : ProductionPermissionCatalog.all()) {
            assertFalse(descriptor.permissionId().startsWith("warehouse."));
        }
        assertFalse(ProductionPermissionCatalog.containsCode("warehouse.transfer.create"));
        assertFalse(ProductionPermissionCatalog.containsCode("warehouse.consumption.create"));
        assertFalse(ProductionPermissionCatalog.containsCode("warehouse.stock.view"));
    }

    @Test
    void capabilityDescriptorExposesPermissionCatalogOnly() {
        ProductionCapability capability = new ProductionCapability();
        assertEquals(ProductionCapability.ID, capability.descriptor().id());
        assertEquals("production", capability.descriptor().id().value());
        assertEquals(7, capability.descriptor().permissions().size());
        assertEquals(0, capability.descriptor().navigationContributions().size());
        assertEquals(0, capability.descriptor().views().size());
        assertEquals(0, capability.descriptor().commands().size());
        assertEquals(
                ProductionPermissions.all().stream().map(PermissionId::value).collect(Collectors.toSet()),
                capability.descriptor().permissions().stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet()));
    }

    @Test
    void descriptorIdsMatchPermissionsAllExactly() {
        assertEquals(
                new HashSet<>(ProductionPermissions.all().stream().map(PermissionId::value).toList()),
                ProductionPermissionCatalog.all().stream()
                        .map(PermissionDescriptor::permissionId)
                        .collect(Collectors.toSet()));
        for (PermissionId permissionId : ProductionPermissions.all()) {
            assertTrue(ProductionPermissionCatalog.contains(permissionId));
        }
    }
}
