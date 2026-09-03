package com.tmp.order.application.query;

import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AuthorizationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Permission-checked Order Management worklist query. Delegates to the read-only persistence port.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Holds injected collaborators managed by the Spring container.")
public final class DefaultOrderWorklistQuery implements OrderWorklistQuery {

    private final OrderQueryReadPort readPort;
    private final AuthorizationService authorization;

    public DefaultOrderWorklistQuery(OrderQueryReadPort readPort, AuthorizationService authorization) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        authorization.requirePermission(OrderManagementPermissions.ORDER_VIEW);
        List<OrderWorklistRowDto> rows = readPort.listWorklistRows(criteria);
        if (rows.size() > OrderWorklistCriteria.MAX_ROWS) {
            throw new IllegalStateException(
                    "Worklist period is too large: more than "
                            + OrderWorklistCriteria.MAX_ROWS
                            + " orders match the selected period");
        }
        return List.copyOf(rows);
    }

    @Override
    public List<OrderCustomerOptionDto> listKnownCustomers() {
        authorization.requirePermission(OrderManagementPermissions.ORDER_VIEW);
        return List.copyOf(readPort.listKnownCustomers());
    }
}
