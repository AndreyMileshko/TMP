package com.tmp.production.domain;

/**
 * Raised when material availability check is requested for an order whose Production View is not
 * {@link OrderProductionViewStatus#IN_PRODUCTION}.
 */
public final class MaterialCheckNotAllowedException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final OrderProductionViewStatus viewStatus;

    public MaterialCheckNotAllowedException(
            SourceOrderId sourceOrderId, OrderProductionViewStatus viewStatus) {
        super(
                "Material availability check is allowed only for IN_PRODUCTION orders; order="
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
