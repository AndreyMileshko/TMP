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

    /**
     * Launch readiness snapshot from Order Management.
     *
     * <p>{@code activeItemCount} counts all ACTIVE items. {@code lines} are ACTIVE items with a
     * current approved Production-facing specification. {@code missingSpecificationItemIds} are
     * ACTIVE items without such a specification. Invariant: {@code activeItemCount == lines.size()
     * + missingSpecificationItemIds.size()}.
     */
    record ResolvedOrderForLaunch(
            SourceOrderId sourceOrderId,
            OrderStatus orderStatus,
            int activeItemCount,
            List<ResolvedItemLine> lines,
            List<SourceOrderItemId> missingSpecificationItemIds) {

        public ResolvedOrderForLaunch {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(orderStatus, "orderStatus");
            Objects.requireNonNull(lines, "lines");
            Objects.requireNonNull(missingSpecificationItemIds, "missingSpecificationItemIds");
            if (activeItemCount < 0) {
                throw new IllegalArgumentException("activeItemCount must be >= 0: " + activeItemCount);
            }
            if (activeItemCount != lines.size() + missingSpecificationItemIds.size()) {
                throw new IllegalArgumentException(
                        "activeItemCount must equal lines + missingSpecificationItemIds: "
                                + activeItemCount
                                + " != "
                                + lines.size()
                                + " + "
                                + missingSpecificationItemIds.size());
            }
            lines = List.copyOf(lines);
            missingSpecificationItemIds = List.copyOf(missingSpecificationItemIds);
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
