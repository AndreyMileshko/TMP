package com.tmp.production.security;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.production.api.ProductionQueryApi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Production Capability: permission catalogue metadata and UI navigation (Production Specification
 * §20).
 *
 * <p>Lifecycle hooks are no-ops. Permissions are not assigned to users or roles here.
 */
public final class ProductionCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("production");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    public static final String NAV_PRODUCTION = "production.nav.workbench";
    public static final String VIEW_PRODUCTION = "production.view.workbench";

    private final CapabilityDescriptor descriptor;

    public ProductionCapability(ProductionQueryApi productionQueryApi) {
        Objects.requireNonNull(productionQueryApi, "productionQueryApi");
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Production")
                        .version(VERSION)
                        .description(
                                "Order-centric production workflow: acceptance, materials, transfer,"
                                        + " receipt, release and cancellation")
                        .permissions(ProductionPermissionCatalog.all())
                        .commands(
                                List.of(
                                        CommandDescriptor.of(
                                                NAV_PRODUCTION,
                                                "Production",
                                                List.of(
                                                        ProductionPermissions.PRODUCTION_VIEW
                                                                .value()))))
                        .views(
                                List.of(
                                        ViewDescriptor.of(
                                                VIEW_PRODUCTION, "Production", NAV_PRODUCTION)))
                        .navigationContributions(
                                List.of(
                                        NavigationContribution.of(
                                                NAV_PRODUCTION, "Производство", VIEW_PRODUCTION, 60)))
                        .publicServices(
                                List.of(
                                        PublicServiceContribution.of(
                                                ProductionQueryApi.class, productionQueryApi)))
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
