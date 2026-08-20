package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.domain.FrozenSpecificationUnavailableException;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.testsupport.ProductionTestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionFoundationQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");

    private TrackingSpecificationQuery specificationQuery;
    private ProductionFoundationQueryService queryService;

    @BeforeEach
    void setUp() {
        specificationQuery = new TrackingSpecificationQuery();
        queryService = new ProductionFoundationQueryService(specificationQuery);
    }

    @Test
    void resolvesFrozenSpecificationByIdOnly() {
        ProductionFoundation foundation = ProductionTestFixtures.sampleFoundation(T0);
        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                foundation.specificationId(),
                                foundation.sourceOrderItemId(),
                                BigDecimal.TEN,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-1",
                                                "Beam",
                                                null,
                                                null,
                                                BigDecimal.valueOf(4),
                                                "PCS"))));

        ProductionItemState state =
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(10), foundation.frozenAt());

        ResolvedSpecification resolved =
                queryService.resolveFrozenSpecification(state).orElseThrow();

        assertEquals(foundation.specificationId(), resolved.specificationId());
        assertEquals(0, specificationQuery.resolveCurrentCalls);
        assertEquals(1, specificationQuery.resolveByIdCalls);
        assertEquals(1, queryService.materialLines(state).size());
    }

    @Test
    void newCurrentSpecificationDoesNotAffectFrozenFoundation() {
        ProductionFoundation foundation = ProductionTestFixtures.sampleFoundation(T0);
        SpecificationId frozenSpecId = foundation.specificationId();

        specificationQuery.byIdSpec =
                Optional.of(
                        new ResolvedSpecification(
                                frozenSpecId,
                                foundation.sourceOrderItemId(),
                                BigDecimal.TEN,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-FROZEN",
                                                "Frozen",
                                                null,
                                                null,
                                                BigDecimal.ONE,
                                                "PCS"))));

        specificationQuery.currentSpec =
                Optional.of(
                        new ResolvedSpecification(
                                SpecificationId.generate(),
                                foundation.sourceOrderItemId(),
                                BigDecimal.TEN,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "MAT-NEW",
                                                "New revision",
                                                null,
                                                null,
                                                BigDecimal.TEN,
                                                "PCS"))));

        ProductionItemState state =
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(10), foundation.frozenAt());

        ResolvedSpecification resolved =
                queryService.resolveFrozenSpecification(state).orElseThrow();

        assertEquals(frozenSpecId, resolved.specificationId());
        assertEquals("MAT-FROZEN", resolved.materialLines().getFirst().materialCode());
        assertEquals(0, specificationQuery.resolveCurrentCalls);
    }

    @Test
    void foundationRemainsImmutableThroughStateTransitions() {
        ProductionFoundation foundation = ProductionTestFixtures.sampleFoundation(T0);
        ProductionItemState launched =
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(5), foundation.frozenAt());
        ProductionItemState released =
                launched.release(ProductionQuantity.positive(2), Instant.parse("2026-08-19T07:00:00Z"));

        assertEquals(foundation, launched.foundation());
        assertEquals(foundation, released.foundation());
        assertEquals(foundation, queryService.foundationOf(released));
    }

    @Test
    void unavailableFrozenSpecificationThrowsExplicitError() {
        ProductionFoundation foundation = ProductionTestFixtures.sampleFoundation(T0);
        assertThrows(
                FrozenSpecificationUnavailableException.class,
                () -> queryService.materialLines(foundation));
    }

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {

        Optional<ResolvedSpecification> currentSpec = Optional.empty();
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();
        int resolveCurrentCalls;
        int resolveByIdCalls;

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            resolveCurrentCalls++;
            return currentSpec;
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            resolveByIdCalls++;
            return byIdSpec;
        }
    }
}
