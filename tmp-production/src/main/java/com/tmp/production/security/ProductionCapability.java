package com.tmp.production.security;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Production Capability: permission catalogue metadata (Production Specification §20).
 *
 * <p>Lifecycle hooks are no-ops. Permissions are not assigned to users or roles here. UI navigation
 * and workbench commands are contributed in STAGE7-017.
 */
public final class ProductionCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("production");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    private final CapabilityDescriptor descriptor;

    public ProductionCapability() {
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Production")
                        .version(VERSION)
                        .description(
                                "Order-centric production workflow: acceptance, materials, transfer,"
                                        + " receipt, release and cancellation")
                        .permissions(ProductionPermissionCatalog.all())
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
