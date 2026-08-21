package com.tmp.production.domain;

/**
 * Raised when Material Transfer Template preparation is requested for an order whose Production
 * View is not {@link OrderProductionViewStatus#IN_PRODUCTION}.
 */
public final class MaterialTransferNotAllowedException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final OrderProductionViewStatus viewStatus;

    public MaterialTransferNotAllowedException(
            SourceOrderId sourceOrderId, OrderProductionViewStatus viewStatus) {
        super(
                "Material transfer template is allowed only for IN_PRODUCTION orders; order="
                        + sourceOrderId
                        + ", view="
                        + viewStatus);
        this.sourceOrderId = sourceOrderId;
        this.viewStatus = viewStatus;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public OrderProductionViewStatus viewStatus() {
        return viewStatus;
    }
}
