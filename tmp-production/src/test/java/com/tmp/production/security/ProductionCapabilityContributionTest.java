package com.tmp.production.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.order.capability.OrderManagementCapability;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.warehouse.security.WarehouseCapability;
import com.tmp.warehouse.security.WarehousePermissions;
import org.junit.jupiter.api.Test;

/**
 * Production capability registration and cross-capability permission ownership matrix.
 */
class ProductionCapabilityContributionTest {

    @Test
    void productionPermissionsAreDiscoverableInCapabilityRegistry() {
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        catalogs.registerInternalContributions(new ProductionCapability().descriptor());

        assertEquals(7, catalogs.activePermissions().size());
        assertEquals(
                ProductionCapability.ID,
                catalogs.permissions().ownerOf(ProductionPermissions.PRODUCTION_VIEW.value()).orElseThrow());
    }

    @Test
    void productionWarehouseOrderAndSecurityCapabilitiesRegisterWithoutDuplicatePermissionIds() {
        CapabilityContributionCatalogs catalogs = new CapabilityContributionCatalogs();
        assertDoesNotThrow(
                () -> {
                    catalogs.registerInternalContributions(new SecurityAdministrationCapability().descriptor());
                    catalogs.registerInternalContributions(new OrderManagementCapability().descriptor());
                    catalogs.registerInternalContributions(new WarehouseCapability().descriptor());
                    catalogs.registerInternalContributions(new ProductionCapability().descriptor());
                });

        long distinctPermissionIds =
                catalogs.activePermissions().stream()
                        .map(com.tmp.capability.api.PermissionDescriptor::permissionId)
                        .distinct()
                        .count();
        assertEquals(catalogs.activePermissions().size(), distinctPermissionIds);
    }

    @Test
    void productionWarehousePermissionRequirementsMatrix() {
        assertEquals(
                ProductionPermissions.PRODUCTION_VIEW,
                permissionForViewProduction());
        assertEquals(
                ProductionPermissions.PRODUCTION_ACCEPT,
                permissionForAcceptOrder());
        assertEquals(
                ProductionPermissions.PRODUCTION_CHECK_MATERIALS,
                permissionForCheckMaterials());
        assertEquals(
                ProductionPermissions.PRODUCTION_CREATE_TRANSFER,
                permissionForCreateMaterialTransfer().production());
        assertEquals(
                WarehousePermissions.WAREHOUSE_TRANSFER,
                permissionForCreateMaterialTransfer().warehouseDownstream());
        assertEquals(
                ProductionPermissions.PRODUCTION_CONFIRM_RECEIPT,
                permissionForConfirmReceipt().production());
        assertEquals(
                WarehousePermissions.WAREHOUSE_TRANSFER,
                permissionForConfirmReceipt().warehouseDownstream());
        assertEquals(
                ProductionPermissions.PRODUCTION_RELEASE,
                permissionForReleaseProducts().production());
        assertEquals(
                WarehousePermissions.WAREHOUSE_CONSUMPTION,
                permissionForReleaseProducts().warehouseDownstream());
        assertEquals(
                ProductionPermissions.PRODUCTION_CANCEL,
                permissionForCancelProduction());
    }

    private static com.tmp.security.api.PermissionId permissionForViewProduction() {
        return ProductionPermissions.PRODUCTION_VIEW;
    }

    private static com.tmp.security.api.PermissionId permissionForAcceptOrder() {
        return ProductionPermissions.PRODUCTION_ACCEPT;
    }

    private static com.tmp.security.api.PermissionId permissionForCheckMaterials() {
        return ProductionPermissions.PRODUCTION_CHECK_MATERIALS;
    }

    private static LayeredPermission permissionForCreateMaterialTransfer() {
        return new LayeredPermission(
                ProductionPermissions.PRODUCTION_CREATE_TRANSFER, WarehousePermissions.WAREHOUSE_TRANSFER);
    }

    private static LayeredPermission permissionForConfirmReceipt() {
        return new LayeredPermission(
                ProductionPermissions.PRODUCTION_CONFIRM_RECEIPT, WarehousePermissions.WAREHOUSE_TRANSFER);
    }

    private static LayeredPermission permissionForReleaseProducts() {
        return new LayeredPermission(
                ProductionPermissions.PRODUCTION_RELEASE, WarehousePermissions.WAREHOUSE_CONSUMPTION);
    }

    private static com.tmp.security.api.PermissionId permissionForCancelProduction() {
        return ProductionPermissions.PRODUCTION_CANCEL;
    }

    private record LayeredPermission(
            com.tmp.security.api.PermissionId production,
            com.tmp.security.api.PermissionId warehouseDownstream) {}
}
