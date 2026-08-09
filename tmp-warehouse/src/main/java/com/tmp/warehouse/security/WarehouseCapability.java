package com.tmp.warehouse.security;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.ViewDescriptor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

/**
 * Warehouse Capability: permission catalogue and UI navigation (Specification §18).
 *
 * <p>Lifecycle hooks are no-ops. Permissions are not assigned to users or roles here.
 */
public final class WarehouseCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("warehouse");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    public static final String NAV_WAREHOUSE = "warehouse.nav.workbench";
    public static final String VIEW_WAREHOUSE = "warehouse.view.workbench";

    private final CapabilityDescriptor descriptor;

    public WarehouseCapability() {
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Warehouse")
                        .version(VERSION)
                        .description(
                                "Warehouse stock state, movements and operations; MaterialReference "
                                        + "from Specification context")
                        .permissions(WarehousePermissionCatalog.all())
                        .commands(
                                List.of(
                                        CommandDescriptor.of(
                                                NAV_WAREHOUSE,
                                                "Warehouse",
                                                List.of(
                                                        WarehousePermissions.WAREHOUSE_VIEW
                                                                .value()))))
                        .views(
                                List.of(
                                        ViewDescriptor.of(
                                                VIEW_WAREHOUSE, "Warehouse", NAV_WAREHOUSE)))
                        .navigationContributions(
                                List.of(
                                        NavigationContribution.of(
                                                NAV_WAREHOUSE, "Склад", VIEW_WAREHOUSE, 50)))
                        .build();
    }

    @Override
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification =
                    "CapabilityDescriptor is an immutable value type; returning it directly is safe.")
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void onInitialize() {
        // no-op: contributions only
    }

    @Override
    public void onActivate() {
        // no-op: contributions only
    }

    @Override
    public void onDeactivate() {
        // no-op: contributions only
    }

    @Override
    public void onStop() {
        // no-op: contributions only
    }
}
