package com.tmp.warehouse.security;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Warehouse Capability: permission catalogue (Specification §18).
 *
 * <p>Lifecycle hooks are no-ops. Permissions are not assigned to users or roles here. Navigation/UI
 * contributions are out of scope for security integration.
 */
public final class WarehouseCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("warehouse");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

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
