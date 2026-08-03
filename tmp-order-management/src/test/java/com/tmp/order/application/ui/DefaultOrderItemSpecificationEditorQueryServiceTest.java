package com.tmp.order.application.ui;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultOrderItemSpecificationEditorQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T10:00:00Z");

    private OrderItemRepository orderItemRepository;
    private AuthorizationService authorization;
    private DefaultOrderItemSpecificationEditorQueryService service;

    @BeforeEach
    void setUp() {
        orderItemRepository = mock(OrderItemRepository.class);
        authorization = mock(AuthorizationService.class);
        service =
                new DefaultOrderItemSpecificationEditorQueryService(
                        orderItemRepository, authorization);
    }

    @Test
    void draftSpecificationReturnsRealLinesInStableOrder() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItem(orderId, itemId, draftNumber)));

        OrderItemSpecificationEditorSnapshot snapshot =
                service.getSpecificationSnapshot(itemId, draftNumber).orElseThrow();

        assertEquals(RevisionStatus.DRAFT, snapshot.revisionStatus());
        assertFalse(snapshot.immutable());
        assertEquals(2, snapshot.lines().size());
        assertEquals("MAT-1", snapshot.lines().get(0).materialCode());
        assertEquals("MAT-2", snapshot.lines().get(1).materialCode());
        assertEquals(1, snapshot.lines().get(0).lineNumber());
        assertEquals(2, snapshot.lines().get(1).lineNumber());
        verify(authorization).requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);
    }

    @Test
    void approvedSpecificationReturnsRealLinesReadOnly() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        OrderItem item = activeWithDraft(orderId, itemId);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        OrderItemSpecificationEditorSnapshot snapshot =
                service.getSpecificationSnapshot(itemId, RevisionNumber.first()).orElseThrow();

        assertEquals(RevisionStatus.APPROVED, snapshot.revisionStatus());
        assertTrue(snapshot.immutable());
        assertEquals(1, snapshot.lines().size());
        assertEquals("MAT-A", snapshot.lines().get(0).materialCode());
    }

    @Test
    void snapshotLinesAreImmutable() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItem(orderId, itemId, draftNumber)));

        OrderItemSpecificationEditorSnapshot snapshot =
                service.getSpecificationSnapshot(itemId, draftNumber).orElseThrow();
        List<OrderItemSpecificationLineView> lines = snapshot.lines();

        assertThrows(UnsupportedOperationException.class, () -> lines.clear());
    }

    @Test
    void missingItemOrRevisionIsEmpty() {
        OrderItemId itemId = OrderItemId.generate();
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.empty());
        assertTrue(service.getSpecificationSnapshot(itemId, RevisionNumber.first()).isEmpty());

        OrderId orderId = OrderId.generate();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(draftItem(orderId, itemId, RevisionNumber.first())));
        assertTrue(service.getSpecificationSnapshot(itemId, RevisionNumber.of(9)).isEmpty());
    }

    @Test
    void draftSnapshotNormalizesScaledImportedProductQuantity() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber draftNumber = RevisionNumber.first();
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(new BigDecimal("8.000000")),
                        null,
                        ItemSpecification.empty(itemId, draftNumber));
        OrderItem item =
                OrderItem.rehydrate(
                        itemId,
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                        OrderItemStatus.DRAFT,
                        null,
                        draftNumber,
                        Map.of(draftNumber, draft),
                        0L,
                        NOW,
                        NOW);
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        OrderItemSpecificationEditorSnapshot snapshot =
                service.getSpecificationSnapshot(itemId, draftNumber).orElseThrow();

        assertEquals(0, snapshot.orderedQuantity().scale());
        assertEquals("8", snapshot.orderedQuantity().toPlainString());
    }

    @Test
    void missingSpecificationViewIsDenied() {
        OrderItemId itemId = OrderItemId.generate();
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorization)
                .requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);

        assertThrows(
                AccessDeniedException.class,
                () -> service.getSpecificationSnapshot(itemId, RevisionNumber.first()));
    }

    private static OrderItem draftItem(
            OrderId orderId, OrderItemId itemId, RevisionNumber draftNumber) {
        ItemSpecification specification =
                ItemSpecification.of(
                        itemId,
                        draftNumber,
                        List.of(
                                IntakeContractFixtures.specLine("MAT-1", "One", BigDecimal.ONE, "pcs"),
                                IntakeContractFixtures.specLine("MAT-2", "Two", BigDecimal.TEN, "m")));
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(3),
                        null,
                        specification);
        return OrderItem.rehydrate(
                itemId,
                orderId,
                ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                OrderItemStatus.DRAFT,
                null,
                draftNumber,
                Map.of(draftNumber, draft),
                0L,
                NOW,
                NOW);
    }

    private static OrderItem activeWithDraft(OrderId orderId, OrderItemId itemId) {
        RevisionNumber activeNumber = RevisionNumber.first();
        RevisionNumber draftNumber = activeNumber.next();
        ItemSpecification activeSpec =
                ItemSpecification.rehydrate(
                        itemId,
                        activeNumber,
                        List.of(
                                IntakeContractFixtures.specLine("MAT-A", "A", BigDecimal.ONE, "pcs")),
                        true);
        ItemSpecification draftSpec =
                ItemSpecification.of(
                        itemId,
                        draftNumber,
                        List.of(
                                IntakeContractFixtures.specLine("MAT-D", "D", BigDecimal.TEN, "pcs")));
        OrderItemRevision active =
                OrderItemRevision.rehydrate(
                        itemId,
                        activeNumber,
                        RevisionStatus.APPROVED,
                        OrderedQuantity.of(2),
                        null,
                        activeSpec);
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(5),
                        activeNumber,
                        draftSpec);
        return OrderItem.rehydrate(
                itemId,
                orderId,
                ItemCommercialData.of(ProductCode.of("P-2"), "Door", null),
                OrderItemStatus.ACTIVE,
                activeNumber,
                draftNumber,
                Map.of(activeNumber, active, draftNumber, draft),
                1L,
                NOW,
                NOW);
    }
}
