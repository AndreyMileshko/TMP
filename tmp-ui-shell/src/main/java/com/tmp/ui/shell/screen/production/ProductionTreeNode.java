package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Presentation node for the Production LEVEL 1 tree. Order and item rows share columns with
 * different semantics — unused cells stay blank rather than fake item values.
 */
public final class ProductionTreeNode {

    public enum Kind {
        ORDER,
        ITEM
    }

    private final Kind kind;
    private final UUID sourceOrderId;
    private final UUID sourceOrderItemId;
    private final String identityLabel;
    private final String secondaryLabel;
    private final String quantityLabel;
    private final String statusLabel;
    private final String releasedLabel;
    private final String remainingLabel;
    private final OrderProductionViewStatus orderStatus;
    private final int displayIndex;
    private final List<ProductionOrderItemRef> childRefs;
    private final boolean selectable;

    private ProductionTreeNode(
            Kind kind,
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            String identityLabel,
            String secondaryLabel,
            String quantityLabel,
            String statusLabel,
            String releasedLabel,
            String remainingLabel,
            OrderProductionViewStatus orderStatus,
            int displayIndex,
            List<ProductionOrderItemRef> childRefs,
            boolean selectable) {
        this.kind = kind;
        this.sourceOrderId = sourceOrderId;
        this.sourceOrderItemId = sourceOrderItemId;
        this.identityLabel = identityLabel;
        this.secondaryLabel = secondaryLabel;
        this.quantityLabel = quantityLabel;
        this.statusLabel = statusLabel;
        this.releasedLabel = releasedLabel;
        this.remainingLabel = remainingLabel;
        this.orderStatus = orderStatus;
        this.displayIndex = displayIndex;
        this.childRefs = List.copyOf(childRefs);
        this.selectable = selectable;
    }

    public static ProductionTreeNode order(
            UUID sourceOrderId,
            String orderNumber,
            String customerName,
            OrderProductionViewStatus status,
            String quantityLabel,
            String releasedLabel,
            String remainingLabel,
            List<ProductionOrderItemRef> selectableChildRefs) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(selectableChildRefs, "selectableChildRefs");
        return new ProductionTreeNode(
                Kind.ORDER,
                sourceOrderId,
                null,
                orderNumber,
                blankToDash(customerName),
                blankToDash(quantityLabel),
                ProductionPresentationLabels.orderStatus(status),
                blankToDash(releasedLabel),
                blankToDash(remainingLabel),
                status,
                0,
                selectableChildRefs,
                !selectableChildRefs.isEmpty());
    }

    public static ProductionTreeNode item(
            UUID sourceOrderId,
            UUID sourceOrderItemId,
            String identityLabel,
            String productLabel,
            String quantityLabel,
            String statusLabel,
            String releasedLabel,
            String remainingLabel,
            int displayIndex,
            boolean selectable) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        Objects.requireNonNull(identityLabel, "identityLabel");
        return new ProductionTreeNode(
                Kind.ITEM,
                sourceOrderId,
                sourceOrderItemId,
                identityLabel,
                blankToDash(productLabel),
                blankToDash(quantityLabel),
                blankToDash(statusLabel),
                blankToDash(releasedLabel),
                blankToDash(remainingLabel),
                null,
                displayIndex,
                List.of(),
                selectable);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isOrder() {
        return kind == Kind.ORDER;
    }

    public boolean isItem() {
        return kind == Kind.ITEM;
    }

    public UUID sourceOrderId() {
        return sourceOrderId;
    }

    public Optional<UUID> sourceOrderItemId() {
        return Optional.ofNullable(sourceOrderItemId);
    }

    public ProductionOrderItemRef itemRef() {
        if (!isItem()) {
            throw new IllegalStateException("Not an item node");
        }
        return new ProductionOrderItemRef(sourceOrderId, sourceOrderItemId);
    }

    public String identityLabel() {
        return identityLabel;
    }

    public String secondaryLabel() {
        return secondaryLabel;
    }

    public String quantityLabel() {
        return quantityLabel;
    }

    public String statusLabel() {
        return statusLabel;
    }

    public String releasedLabel() {
        return releasedLabel;
    }

    public String remainingLabel() {
        return remainingLabel;
    }

    public Optional<OrderProductionViewStatus> orderStatus() {
        return Optional.ofNullable(orderStatus);
    }

    public int displayIndex() {
        return displayIndex;
    }

    /** Selectable children for order checkbox state; empty for item nodes. */
    public List<ProductionOrderItemRef> childRefs() {
        return childRefs;
    }

    /**
     * Item: whether the checkbox may be toggled. Order: whether the parent checkbox is enabled
     * (has at least one selectable child).
     */
    public boolean selectable() {
        return selectable;
    }

    public static String humanReadablePosition(String externalPositionNumber, int displayIndex1Based) {
        if (externalPositionNumber != null && !externalPositionNumber.isBlank()) {
            String trimmed = externalPositionNumber.trim();
            if (trimmed.regionMatches(true, 0, "Поз", 0, 3)) {
                return trimmed;
            }
            return "Поз. " + trimmed;
        }
        return "Позиция " + displayIndex1Based;
    }

    private static String blankToDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value;
    }
}
