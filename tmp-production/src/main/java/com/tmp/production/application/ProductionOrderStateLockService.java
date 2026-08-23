package com.tmp.production.application;

import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import java.util.List;
import java.util.Objects;

/**
 * Reusable whole-order Production state locking boundary for mutating workflows (Release,
 * future Cancellation).
 *
 * <p>Delegates to {@link ProductionOrderViewService} so repository access stays within allowed
 * read/lock boundaries. {@link #lockAllItemStates} must be invoked inside an ambient
 * {@code REQUIRED} application transaction.
 */
public final class ProductionOrderStateLockService {

    private final ProductionOrderViewService orderViewService;

    public ProductionOrderStateLockService(ProductionOrderViewService orderViewService) {
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
    }

    /** Read-only snapshot (no row lock). Safe for informational preview. */
    public List<ProductionItemState> readAllItemStates(SourceOrderId sourceOrderId) {
        return orderViewService.listItemStates(sourceOrderId);
    }

    /**
     * Row-locks and returns all Production item states for the order until commit/rollback.
     *
     * <p>Callers must treat the returned list as the transaction-consistent snapshot for validation,
     * plan calculation and downstream mutations.
     */
    public List<ProductionItemState> lockAllItemStates(SourceOrderId sourceOrderId) {
        return orderViewService.lockAllItemStates(sourceOrderId);
    }
}
