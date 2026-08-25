package com.tmp.production.integration.publicboundary;

import com.tmp.security.api.PermissionId;
import java.util.List;

/**
 * Permission identifiers expressed only via {@link PermissionId} (no OM/Warehouse capability
 * package imports).
 */
final class PublicBoundaryPermissions {

    static final List<PermissionId> ORDER_IMPORT_AND_VIEW =
            List.of(
                    PermissionId.of("order.order.view"),
                    PermissionId.of("order.order.create"),
                    PermissionId.of("order.order.approve"),
                    PermissionId.of("order.item.view"),
                    PermissionId.of("order.item.create"),
                    PermissionId.of("order.item.edit"),
                    PermissionId.of("order.item.approve"),
                    PermissionId.of("order.revision.create"),
                    PermissionId.of("order.revision.edit"),
                    PermissionId.of("order.specification.view"));

    static final List<PermissionId> WAREHOUSE_COMMAND_AND_QUERY =
            List.of(
                    PermissionId.of("warehouse.stock.view"),
                    PermissionId.of("warehouse.receipt.create"),
                    PermissionId.of("warehouse.transfer.create"),
                    PermissionId.of("warehouse.consumption.create"),
                    PermissionId.of("warehouse.warehouse.view"),
                    PermissionId.of("warehouse.warehouse.create"),
                    PermissionId.of("warehouse.storage-cell.view"),
                    PermissionId.of("warehouse.storage-cell.create"));

    static final List<PermissionId> PRODUCTION_ALL =
            List.of(
                    PermissionId.of("production.order.view"),
                    PermissionId.of("production.order.accept"),
                    PermissionId.of("production.materials.check"),
                    PermissionId.of("production.transfer.create"),
                    PermissionId.of("production.receipt.confirm"),
                    PermissionId.of("production.release.create"),
                    PermissionId.of("production.cancellation.create"));

    private PublicBoundaryPermissions() {}
}
