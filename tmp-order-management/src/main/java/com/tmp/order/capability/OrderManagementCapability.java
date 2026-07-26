package com.tmp.order.capability;

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
 * Order Management Capability: permission catalogue (Specification §18) and UI navigation metadata
 * for the order list screen.
 *
 * <p>Lifecycle hooks are no-ops. Permissions are not assigned to users or roles here.
 */
public final class OrderManagementCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("order-management");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    public static final String NAV_ORDERS = "order.nav.orders";
    public static final String VIEW_ORDERS = "order.view.orders";

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
                        .commands(List.of(
                                CommandDescriptor.of(
                                        NAV_ORDERS,
                                        "Orders",
                                        List.of(OrderManagementPermissions.ORDER_VIEW.value()))))
                        .views(List.of(ViewDescriptor.of(VIEW_ORDERS, "Orders", NAV_ORDERS)))
                        .navigationContributions(List.of(
                                NavigationContribution.of(NAV_ORDERS, "Заказы", VIEW_ORDERS, 40)))
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
