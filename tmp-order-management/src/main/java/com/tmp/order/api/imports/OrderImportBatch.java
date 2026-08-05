package com.tmp.order.api.imports;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Source-neutral import batch for one order (Specification §27 / Final STXT Contract). One STXT
 * file may yield several batches (several Orders). Independent of STXT, JavaFX, Firebird and
 * filesystem APIs. {@code contentChecksum} is ephemeral adapter metadata for plan binding; Import
 * Core does not persist checksums (ADR-031).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "positions is an unmodifiable defensive copy created at construction.")
public final class OrderImportBatch {

    private final String sourceType;
    private final String sourceReference;
    private final String contentChecksum;
    private final String orderNumber;
    private final LocalDate orderDate;
    private final LocalDate readyDate;
    private final String customerName;
    private final List<OrderImportPosition> positions;

    private OrderImportBatch(
            String sourceType,
            String sourceReference,
            String contentChecksum,
            String orderNumber,
            LocalDate orderDate,
            LocalDate readyDate,
            String customerName,
            List<OrderImportPosition> positions) {
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.contentChecksum = contentChecksum;
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.readyDate = readyDate;
        this.customerName = customerName;
        this.positions = positions;
    }

    /**
     * Creates an immutable batch without commercial header fields (legacy callers).
     *
     * @deprecated use {@link #of(String, String, String, String, LocalDate, LocalDate, String, List)}
     */
    @Deprecated
    public static OrderImportBatch of(
            String sourceType,
            String sourceReference,
            String contentChecksum,
            String orderNumber,
            List<OrderImportPosition> positions) {
        return of(
                sourceType,
                sourceReference,
                contentChecksum,
                orderNumber,
                null,
                null,
                null,
                positions);
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
            LocalDate orderDate,
            LocalDate readyDate,
            String customerName,
            List<OrderImportPosition> positions) {
        Objects.requireNonNull(positions, "positions");
        List<OrderImportPosition> copy = new ArrayList<>(positions.size());
        for (OrderImportPosition position : positions) {
            copy.add(Objects.requireNonNull(position, "position"));
        }
        String normalizedCustomer =
                customerName == null || customerName.isBlank() ? null : customerName.trim();
        return new OrderImportBatch(
                sourceType,
                sourceReference,
                contentChecksum,
                orderNumber,
                orderDate,
                readyDate,
                normalizedCustomer,
                List.copyOf(copy));
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

    /** Order date from source «Дата заказа»; required for ACTIVE import validation. */
    public LocalDate orderDate() {
        return orderDate;
    }

    /** Optional readiness date from source «Дата готовности». */
    public LocalDate readyDate() {
        return readyDate;
    }

    /** Customer from source «Клиент»; required for ACTIVE import validation. */
    public String customerName() {
        return customerName;
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
            if (position.quantity() != null) {
                total = total.add(BigDecimal.valueOf(position.quantity()));
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
                && Objects.equals(orderDate, that.orderDate)
                && Objects.equals(readyDate, that.readyDate)
                && Objects.equals(customerName, that.customerName)
                && positions.equals(that.positions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceType,
                sourceReference,
                contentChecksum,
                orderNumber,
                orderDate,
                readyDate,
                customerName,
                positions);
    }
}
