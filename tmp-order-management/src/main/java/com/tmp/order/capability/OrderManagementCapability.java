package com.tmp.order.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Order Management Capability: declares the permission catalogue from Specification §18.
 *
 * <p>Lifecycle hooks are no-ops. Navigation, views and UI contributions are out of scope for
 * STAGE5-035. Permissions are not assigned to users or roles here.
 */
public final class OrderManagementCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("order-management");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    private final CapabilityDescriptor descriptor;

    public OrderManagementCapability() {
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Order Management")
                        .version(VERSION)
                        .description(
                                "Commercial lifecycle of customer orders, items, revisions and "
                                        + "item specifications")
                        .permissions(OrderManagementPermissionCatalog.all())
                        .build();
    }

    @Override
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "CapabilityDescriptor is an immutable value type; returning it directly is safe.")
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void onInitialize() {
        // no-op: permission contributions only
    }

    @Override
    public void onActivate() {
        // no-op: permission contributions only
    }

    @Override
    public void onDeactivate() {
        // no-op: permission contributions only
    }

    @Override
    public void onStop() {
        // no-op: permission contributions only
    }
}
