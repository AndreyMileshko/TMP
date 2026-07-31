package com.tmp.order.api.imports;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One calculation position inside a source-neutral {@link OrderImportBatch}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "specificationLines is an unmodifiable defensive copy.")
public final class OrderImportPosition {

    private final String externalPositionNumber;
    private final Integer productQuantity;
    private final List<OrderImportSpecificationLine> specificationLines;

    private OrderImportPosition(
            String externalPositionNumber,
            Integer productQuantity,
            List<OrderImportSpecificationLine> specificationLines) {
        this.externalPositionNumber = externalPositionNumber;
        this.productQuantity = productQuantity;
        this.specificationLines = specificationLines;
    }

    public static OrderImportPosition of(
            String externalPositionNumber,
            Integer productQuantity,
            List<OrderImportSpecificationLine> specificationLines) {
        Objects.requireNonNull(specificationLines, "specificationLines");
        List<OrderImportSpecificationLine> copy = new ArrayList<>(specificationLines.size());
        for (OrderImportSpecificationLine line : specificationLines) {
            copy.add(Objects.requireNonNull(line, "specificationLine"));
        }
        return new OrderImportPosition(
                externalPositionNumber, productQuantity, List.copyOf(copy));
    }

    public String externalPositionNumber() {
        return externalPositionNumber;
    }

    public Integer productQuantity() {
        return productQuantity;
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
                && Objects.equals(productQuantity, that.productQuantity)
                && specificationLines.equals(that.specificationLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalPositionNumber, productQuantity, specificationLines);
    }
}
