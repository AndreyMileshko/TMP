package com.tmp.order.api.imports;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One calculation position inside a source-neutral {@link OrderImportBatch}.
 *
 * <p>Final STXT Contract: {@code externalPositionNumber} («Изделие»), {@code productCode}
 * («Код изделия»), {@code name} («Наименование изделия»), {@code quantity} («Кол-во изд.»).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "specificationLines is an unmodifiable defensive copy.")
public final class OrderImportPosition {

    private final String externalPositionNumber;
    private final String productCode;
    private final String name;
    private final Integer quantity;
    private final List<OrderImportSpecificationLine> specificationLines;

    private OrderImportPosition(
            String externalPositionNumber,
            String productCode,
            String name,
            Integer quantity,
            List<OrderImportSpecificationLine> specificationLines) {
        this.externalPositionNumber = externalPositionNumber;
        this.productCode = productCode;
        this.name = name;
        this.quantity = quantity;
        this.specificationLines = specificationLines;
    }

    /**
     * @deprecated use {@link #of(String, String, String, Integer, List)}
     */
    @Deprecated
    public static OrderImportPosition of(
            String externalPositionNumber,
            Integer productQuantity,
            List<OrderImportSpecificationLine> specificationLines) {
        return of(externalPositionNumber, null, null, productQuantity, specificationLines);
    }

    public static OrderImportPosition of(
            String externalPositionNumber,
            String productCode,
            String name,
            Integer quantity,
            List<OrderImportSpecificationLine> specificationLines) {
        Objects.requireNonNull(specificationLines, "specificationLines");
        List<OrderImportSpecificationLine> copy = new ArrayList<>(specificationLines.size());
        for (OrderImportSpecificationLine line : specificationLines) {
            copy.add(Objects.requireNonNull(line, "specificationLine"));
        }
        return new OrderImportPosition(
                externalPositionNumber,
                blankToNull(productCode),
                blankToNull(name),
                quantity,
                List.copyOf(copy));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String externalPositionNumber() {
        return externalPositionNumber;
    }

    public String productCode() {
        return productCode;
    }

    public String name() {
        return name;
    }

    /** Ordered product copies («Кол-во изд.»). */
    public Integer quantity() {
        return quantity;
    }

    /** Alias for {@link #quantity()} (legacy import callers). */
    public Integer productQuantity() {
        return quantity;
    }

    public List<OrderImportSpecificationLine> specificationLines() {
        return specificationLines;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderImportPosition that)) {
            return false;
        }
        return Objects.equals(externalPositionNumber, that.externalPositionNumber)
                && Objects.equals(productCode, that.productCode)
                && Objects.equals(name, that.name)
                && Objects.equals(quantity, that.quantity)
                && specificationLines.equals(that.specificationLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                externalPositionNumber, productCode, name, quantity, specificationLines);
    }
}
