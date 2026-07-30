package com.tmp.order.api.ui;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Desktop UI read model for the order item / revision editor.
 *
 * <p>May include an optional Draft Revision. Not part of the Public Query API.
 */
public final class OrderItemEditorSnapshot {

    private final OrderItemId orderItemId;
    private final OrderId orderId;
    private final String productCode;
    private final String name;
    private final String comments;
    private final String externalPositionNumber;
    private final OrderItemStatus status;
    private final RevisionView activeRevision;
    private final RevisionView draftRevision;
    private final BigDecimal orderedQuantity;

    private OrderItemEditorSnapshot(
            OrderItemId orderItemId,
            OrderId orderId,
            String productCode,
            String name,
            String comments,
            String externalPositionNumber,
            OrderItemStatus status,
            RevisionView activeRevision,
            RevisionView draftRevision,
            BigDecimal orderedQuantity) {
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.productCode = productCode;
        this.name = name;
        this.comments = comments;
        this.externalPositionNumber = externalPositionNumber;
        this.status = status;
        this.activeRevision = activeRevision;
        this.draftRevision = draftRevision;
        this.orderedQuantity = orderedQuantity;
    }

    public static OrderItemEditorSnapshot of(
            OrderItemId orderItemId,
            OrderId orderId,
            String productCode,
            String name,
            String comments,
            String externalPositionNumber,
            OrderItemStatus status,
            RevisionView activeRevision,
            RevisionView draftRevision,
            BigDecimal orderedQuantity) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(productCode, "productCode");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        return new OrderItemEditorSnapshot(
                orderItemId,
                orderId,
                productCode,
                name,
                comments,
                externalPositionNumber,
                status,
                activeRevision,
                draftRevision,
                orderedQuantity);
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public OrderId orderId() {
        return orderId;
    }

    public String productCode() {
        return productCode;
    }

    public String name() {
        return name;
    }

    public String comments() {
        return comments;
    }

    public String externalPositionNumber() {
        return externalPositionNumber;
    }

    public OrderItemStatus status() {
        return status;
    }

    public Optional<RevisionView> activeRevision() {
        return Optional.ofNullable(activeRevision);
    }

    public Optional<RevisionView> draftRevision() {
        return Optional.ofNullable(draftRevision);
    }

    /** Quantity of the draft revision when present; otherwise of the active revision. */
    public BigDecimal orderedQuantity() {
        return orderedQuantity;
    }

    public Optional<RevisionNumber> activeRevisionNumber() {
        return activeRevision().map(RevisionView::revisionNumber);
    }

    public Optional<RevisionNumber> draftRevisionNumber() {
        return draftRevision().map(RevisionView::revisionNumber);
    }

    public int draftSpecificationLineCount() {
        return draftRevision == null ? 0 : draftRevision.specificationLineCount();
    }

    /** Revision slice for the item editor (active or draft). */
    public static final class RevisionView {

        private final RevisionNumber revisionNumber;
        private final RevisionStatus status;
        private final BigDecimal orderedQuantity;
        private final int specificationLineCount;

        private RevisionView(
                RevisionNumber revisionNumber,
                RevisionStatus status,
                BigDecimal orderedQuantity,
                int specificationLineCount) {
            this.revisionNumber = revisionNumber;
            this.status = status;
            this.orderedQuantity = orderedQuantity;
            this.specificationLineCount = specificationLineCount;
        }

        public static RevisionView of(
                RevisionNumber revisionNumber,
                RevisionStatus status,
                BigDecimal orderedQuantity,
                int specificationLineCount) {
            Objects.requireNonNull(revisionNumber, "revisionNumber");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(orderedQuantity, "orderedQuantity");
            if (specificationLineCount < 0) {
                throw new IllegalArgumentException(
                        "specificationLineCount must be >= 0: " + specificationLineCount);
            }
            return new RevisionView(revisionNumber, status, orderedQuantity, specificationLineCount);
        }

        public RevisionNumber revisionNumber() {
            return revisionNumber;
        }

        public RevisionStatus status() {
            return status;
        }

        public BigDecimal orderedQuantity() {
            return orderedQuantity;
        }

        public int specificationLineCount() {
            return specificationLineCount;
        }
    }
}
