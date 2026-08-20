package com.tmp.production.testsupport;

import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;

public final class ProductionTestFixtures {

    private ProductionTestFixtures() {}

    public static ProductionFoundation sampleFoundation(Instant frozenAt) {
        return ProductionFoundation.freeze(
                SourceOrderId.generate(),
                SourceOrderItemId.generate(),
                SpecificationId.generate(),
                frozenAt);
    }
}
