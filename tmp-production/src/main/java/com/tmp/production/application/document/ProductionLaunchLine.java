package com.tmp.production.application.document;

import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import java.util.Objects;

/** One order item line within a whole-order Production Launch document. */
public record ProductionLaunchLine(
        ProductionFoundation foundation,
        ProductionQuantity orderedQuantity,
        CuttingPlanLinks cuttingPlanLinks) {

    public ProductionLaunchLine {
        Objects.requireNonNull(foundation, "foundation");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        if (cuttingPlanLinks == null) {
            cuttingPlanLinks = CuttingPlanLinks.empty();
        }
    }

    /** Launch line without Cutting Plan links (Stage 8 absent / empty default). */
    public ProductionLaunchLine(ProductionFoundation foundation, ProductionQuantity orderedQuantity) {
        this(foundation, orderedQuantity, CuttingPlanLinks.empty());
    }
}
