package com.tmp.order.api.imports;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Source-neutral import batch (Specification §27.4). Independent of STXT, JavaFX, Firebird and
 * filesystem APIs. {@code contentChecksum} is supplied by the source adapter; Import Core does not
 * read bytes or compute checksums.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "positions is an unmodifiable defensive copy created at construction.")
public final class OrderImportBatch {

    private final String sourceType;
    private final String sourceReference;
    private final String contentChecksum;
    private final String orderNumber;
    private final List<OrderImportPosition> positions;

    private OrderImportBatch(
            String sourceType,
            String sourceReference,
            String contentChecksum,
            String orderNumber,
            List<OrderImportPosition> positions) {
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.contentChecksum = contentChecksum;
        this.orderNumber = orderNumber;
        this.positions = positions;
    }

    /**
     * Creates an immutable batch. Lists are defensively copied. Null/blank rules for business
     * fields are enforced by {@link OrderImportService#preview}; construction only rejects {@code
     * null} collection and copies elements.
     */
    public static OrderImportBatch of(
            String sourceType,
            String sourceReference,
            String contentChecksum,
            String orderNumber,
            List<OrderImportPosition> positions) {
        Objects.requireNonNull(positions, "positions");
        List<OrderImportPosition> copy = new ArrayList<>(positions.size());
        for (OrderImportPosition position : positions) {
            copy.add(Objects.requireNonNull(position, "position"));
        }
        return new OrderImportBatch(
                sourceType, sourceReference, contentChecksum, orderNumber, List.copyOf(copy));
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceReference() {
        return sourceReference;
    }

    public String contentChecksum() {
        return contentChecksum;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public List<OrderImportPosition> positions() {
        return positions;
    }

    public int positionCount() {
        return positions.size();
    }

    public BigDecimal totalProductQuantity() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderImportPosition position : positions) {
            if (position.productQuantity() != null) {
                total = total.add(BigDecimal.valueOf(position.productQuantity()));
            }
        }
        return total;
    }

    public int specificationLineCount() {
        int count = 0;
        for (OrderImportPosition position : positions) {
            count += position.specificationLines().size();
        }
        return count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderImportBatch that)) {
            return false;
        }
        return Objects.equals(sourceType, that.sourceType)
                && Objects.equals(sourceReference, that.sourceReference)
                && Objects.equals(contentChecksum, that.contentChecksum)
                && Objects.equals(orderNumber, that.orderNumber)
                && positions.equals(that.positions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, sourceReference, contentChecksum, orderNumber, positions);
    }
}
