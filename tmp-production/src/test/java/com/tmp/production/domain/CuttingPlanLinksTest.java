package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CuttingPlanLinksTest {

    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");

    @Test
    void launchWithoutLinksDefaultsToEmpty() {
        ProductionItemState state =
                ProductionItemState.launch(
                        sampleFoundation(), ProductionQuantity.positive(2), T0);

        assertTrue(state.cuttingPlanLinks().isEmpty());
        assertEquals(0, state.cuttingPlanLinks().size());
    }

    @Test
    void launchWithOneLinkPersistsInState() {
        MaterialReferenceId material = MaterialReferenceId.generate();
        CuttingPlanId plan = CuttingPlanId.generate();
        CuttingPlanLinks links =
                CuttingPlanLinks.of(ProductionCuttingPlanLink.of(material, plan));

        ProductionItemState state =
                ProductionItemState.launch(
                        sampleFoundation(), ProductionQuantity.positive(1), T0, links);

        assertEquals(1, state.cuttingPlanLinks().size());
        assertEquals(plan, state.cuttingPlanLinks().findCuttingPlanId(material).orElseThrow());
    }

    @Test
    void launchWithMultipleLinksSupportsZeroToN() {
        MaterialReferenceId a = MaterialReferenceId.generate();
        MaterialReferenceId b = MaterialReferenceId.generate();
        MaterialReferenceId c = MaterialReferenceId.generate();
        CuttingPlanId x = CuttingPlanId.generate();
        CuttingPlanId y = CuttingPlanId.generate();
        CuttingPlanId z = CuttingPlanId.generate();

        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(a, x),
                        ProductionCuttingPlanLink.of(b, y),
                        ProductionCuttingPlanLink.of(c, z));

        ProductionItemState state =
                ProductionItemState.launch(
                        sampleFoundation(), ProductionQuantity.positive(3), T0, links);

        assertEquals(3, state.cuttingPlanLinks().size());
        assertEquals(x, state.cuttingPlanLinks().findCuttingPlanId(a).orElseThrow());
        assertEquals(y, state.cuttingPlanLinks().findCuttingPlanId(b).orElseThrow());
        assertEquals(z, state.cuttingPlanLinks().findCuttingPlanId(c).orElseThrow());
    }

    @Test
    void duplicateMaterialReferenceIsRejectedByDomain() {
        MaterialReferenceId material = MaterialReferenceId.generate();

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                CuttingPlanLinks.of(
                                        ProductionCuttingPlanLink.of(
                                                material, CuttingPlanId.generate()),
                                        ProductionCuttingPlanLink.of(
                                                material, CuttingPlanId.generate())));

        assertTrue(ex.getMessage().contains("duplicate"));
    }

    @Test
    void sameCuttingPlanMayBeUsedByMultipleItems() {
        CuttingPlanId shared = CuttingPlanId.generate();
        MaterialReferenceId material = MaterialReferenceId.generate();

        ProductionItemState itemA =
                ProductionItemState.launch(
                        sampleFoundation(),
                        ProductionQuantity.positive(1),
                        T0,
                        CuttingPlanLinks.of(ProductionCuttingPlanLink.of(material, shared)));
        ProductionItemState itemB =
                ProductionItemState.launch(
                        sampleFoundation(),
                        ProductionQuantity.positive(2),
                        T0,
                        CuttingPlanLinks.of(ProductionCuttingPlanLink.of(material, shared)));

        assertEquals(shared, itemA.cuttingPlanLinks().asList().getFirst().cuttingPlanId());
        assertEquals(shared, itemB.cuttingPlanLinks().asList().getFirst().cuttingPlanId());
        assertNotEquals(itemA.sourceOrderItemId(), itemB.sourceOrderItemId());
    }

    @Test
    void returnedLinkCollectionIsImmutable() {
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()));

        assertThrows(UnsupportedOperationException.class, () -> links.asList().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        links.asList()
                                .add(
                                        ProductionCuttingPlanLink.of(
                                                MaterialReferenceId.generate(),
                                                CuttingPlanId.generate())));
    }

    @Test
    void rehydratePreservesLinksSemanticEquality() {
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()),
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()));
        ProductionFoundation foundation = sampleFoundation();

        ProductionItemState original =
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(4), T0, links);
        ProductionItemState restored =
                ProductionItemState.rehydrate(
                        foundation,
                        original.status(),
                        original.orderedQuantity(),
                        original.launchedQuantity(),
                        original.activeProductionQuantity(),
                        original.releasedQuantity(),
                        original.lastMaterialCheckAt(),
                        original.lastStatusChangedAt(),
                        original.cuttingPlanLinks());

        assertEquals(original.cuttingPlanLinks(), restored.cuttingPlanLinks());
        assertEquals(2, restored.cuttingPlanLinks().size());
    }

    @Test
    void releaseAndCancelPreserveLinks() {
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()));
        ProductionItemState launched =
                ProductionItemState.launch(
                        sampleFoundation(), ProductionQuantity.positive(5), T0, links);

        ProductionItemState partial = launched.release(ProductionQuantity.positive(2), T0.plusSeconds(60));
        ProductionItemState cancelled = partial.cancel(T0.plusSeconds(120));

        assertEquals(links, launched.cuttingPlanLinks());
        assertEquals(links, partial.cuttingPlanLinks());
        assertEquals(links, cancelled.cuttingPlanLinks());
    }

    @Test
    void cuttingLinkModelHasNoRevisionTypesOrFields() {
        assertNoRevision(CuttingPlanId.class);
        assertNoRevision(MaterialReferenceId.class);
        assertNoRevision(ProductionCuttingPlanLink.class);
        assertNoRevision(CuttingPlanLinks.class);

        Set<String> fieldNames =
                Arrays.stream(ProductionCuttingPlanLink.class.getDeclaredFields())
                        .map(Field::getName)
                        .collect(Collectors.toSet());
        assertTrue(fieldNames.stream().noneMatch(n -> n.toLowerCase().contains("revision")));

        Set<String> methodNames =
                Arrays.stream(ProductionCuttingPlanLink.class.getDeclaredMethods())
                        .map(Method::getName)
                        .collect(Collectors.toSet());
        assertTrue(methodNames.stream().noneMatch(n -> n.toLowerCase().contains("revision")));
    }

    @Test
    void cuttingPlanIdIsOpaqueUuidWithValueEquality() {
        java.util.UUID value = java.util.UUID.randomUUID();
        assertEquals(CuttingPlanId.of(value), CuttingPlanId.of(value));
        assertEquals(value, CuttingPlanId.of(value).value());
        assertThrows(NullPointerException.class, () -> CuttingPlanId.of(null));
    }

    private static void assertNoRevision(Class<?> type) {
        Set<String> fields =
                Arrays.stream(type.getDeclaredFields())
                        .map(Field::getName)
                        .filter(n -> n.toLowerCase().contains("revision"))
                        .collect(Collectors.toSet());
        assertTrue(fields.isEmpty(), type.getSimpleName() + " has revision fields: " + fields);
    }

    private static ProductionFoundation sampleFoundation() {
        return ProductionFoundation.freeze(
                SourceOrderId.generate(),
                SourceOrderItemId.generate(),
                SpecificationId.generate(),
                T0);
    }
}
