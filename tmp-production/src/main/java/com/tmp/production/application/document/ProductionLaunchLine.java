package com.tmp.production.application.document;

import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import java.util.Objects;

/** One order item line within a whole-order Production Launch document. */
public record ProductionLaunchLine(ProductionFoundation foundation, ProductionQuantity orderedQuantity) {

    public ProductionLaunchLine {
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
    }
}
