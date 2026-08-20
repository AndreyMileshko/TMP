package com.tmp.production.application.port;

import com.tmp.order.api.OrderStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Port for resolving a customer order and its producible items via OM Public Query API only.
 */
public interface OrderForProductionQueryPort {

    Optional<ResolvedOrderForLaunch> resolveForLaunch(SourceOrderId sourceOrderId);

    record ResolvedOrderForLaunch(
            SourceOrderId sourceOrderId,
            OrderStatus orderStatus,
            int activeItemCount,
            List<ResolvedItemLine> lines) {

        public ResolvedOrderForLaunch {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(orderStatus, "orderStatus");
            Objects.requireNonNull(lines, "lines");
            if (activeItemCount < 0) {
                throw new IllegalArgumentException("activeItemCount must be >= 0: " + activeItemCount);
            }
            lines = List.copyOf(lines);
        }
    }

    record ResolvedItemLine(
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId,
            BigDecimal orderedQuantity) {

        public ResolvedItemLine {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        }
    }
}
