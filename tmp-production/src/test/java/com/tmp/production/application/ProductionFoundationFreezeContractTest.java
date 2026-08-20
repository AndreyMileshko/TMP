package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contract tests for Production Foundation freeze semantics (STAGE7-005). */
class ProductionFoundationFreezeContractTest {

    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");

    private DualSpecificationQuery specificationQuery;
    private ProductionFoundationQueryService queryService;

    @BeforeEach
    void setUp() {
        specificationQuery = new DualSpecificationQuery();
        queryService = new ProductionFoundationQueryService(specificationQuery);
    }

    @Test
    void frozenSpecificationIdNeverChangesAfterLaunch() {
        SpecificationId frozenId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        com.tmp.production.domain.SourceOrderId.generate(),
                        itemId,
                        frozenId,
                        T0);

        specificationQuery.registerById(frozenId, materialLine("FROZEN", 1));
        specificationQuery.registerCurrent(itemId, materialLine("CURRENT", 99));

        ProductionItemState state =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(5), T0);

        ResolvedSpecification resolved =
                queryService.resolveFrozenSpecification(state).orElseThrow();

        assertEquals(frozenId, resolved.specificationId());
        assertEquals("FROZEN", resolved.materialLines().getFirst().materialCode());
        assertNotEquals("CURRENT", resolved.materialLines().getFirst().materialCode());
        assertEquals(0, specificationQuery.resolveCurrentCalls);
        assertEquals(1, specificationQuery.resolveByIdCalls);
    }

    @Test
    void getSpecificationByIdReturnsConsistentResultForSameFoundation() {
        SpecificationId frozenId = SpecificationId.generate();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        com.tmp.production.domain.SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        frozenId,
                        T0);
        specificationQuery.registerById(frozenId, materialLine("MAT-A", 2));

        ProductionItemState state =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(1), T0);

        ResolvedSpecification first = queryService.resolveFrozenSpecification(state).orElseThrow();
        ResolvedSpecification second = queryService.resolveFrozenSpecification(state).orElseThrow();

        assertEquals(first, second);
        assertEquals(2, specificationQuery.resolveByIdCalls);
    }

    private static ResolvedMaterialLine materialLine(String code, int quantity) {
        return new ResolvedMaterialLine(
                code, code + "-name", null, null, BigDecimal.valueOf(quantity), "PCS");
    }

    private static final class DualSpecificationQuery implements OrderSpecificationQueryPort {

        private ResolvedSpecification current;
        private ResolvedSpecification byId;
        int resolveCurrentCalls;
        int resolveByIdCalls;

        void registerCurrent(SourceOrderItemId itemId, ResolvedMaterialLine line) {
            current =
                    new ResolvedSpecification(
                            SpecificationId.generate(),
                            itemId,
                            BigDecimal.TEN,
                            List.of(line));
        }

        void registerById(SpecificationId specificationId, ResolvedMaterialLine line) {
            byId =
                    new ResolvedSpecification(
                            specificationId,
                            SourceOrderItemId.generate(),
                            BigDecimal.TEN,
                            List.of(line));
        }

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            resolveCurrentCalls++;
            return Optional.ofNullable(current);
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            resolveByIdCalls++;
            return Optional.ofNullable(byId);
        }
    }
}
