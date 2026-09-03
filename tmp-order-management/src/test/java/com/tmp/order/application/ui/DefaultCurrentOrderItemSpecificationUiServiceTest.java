package com.tmp.order.application.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationRef;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultCurrentOrderItemSpecificationUiServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T05:00:00Z");

    private OrderItemRepository orderItemRepository;
    private AuthorizationService authorization;
    private DefaultCurrentOrderItemSpecificationUiService service;

    @BeforeEach
    void setUp() {
        orderItemRepository = mock(OrderItemRepository.class);
        authorization = mock(AuthorizationService.class);
        service =
                new DefaultCurrentOrderItemSpecificationUiService(
                        orderItemRepository, authorization);
    }

    @Test
    void prefersDraftRevisionWhenPresent() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber active = RevisionNumber.first();
        RevisionNumber draft = active.next();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(itemWithActiveAndDraft(orderId, itemId, active, draft)));

        Optional<CurrentOrderItemSpecificationRef> resolved = service.resolveCurrent(itemId);

        assertTrue(resolved.isPresent());
        assertEquals(itemId, resolved.get().orderItemId());
        assertEquals(draft, resolved.get().revisionNumber());
        verify(authorization).requirePermission(OrderManagementPermissions.ITEM_VIEW);
    }

    @Test
    void fallsBackToActiveRevisionWhenNoDraft() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber active = RevisionNumber.first();
        when(orderItemRepository.findById(itemId))
                .thenReturn(Optional.of(itemWithActiveOnly(orderId, itemId, active)));

        Optional<CurrentOrderItemSpecificationRef> resolved = service.resolveCurrent(itemId);

        assertTrue(resolved.isPresent());
        assertEquals(active, resolved.get().revisionNumber());
    }

    @Test
    void returnsEmptyWhenItemMissing() {
        OrderItemId itemId = OrderItemId.generate();
        when(orderItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertTrue(service.resolveCurrent(itemId).isEmpty());
    }

    private static OrderItem itemWithActiveAndDraft(
            OrderId orderId,
            OrderItemId itemId,
            RevisionNumber activeNumber,
            RevisionNumber draftNumber) {
        OrderItemRevision active =
                OrderItemRevision.rehydrate(
                        itemId,
                        activeNumber,
                        RevisionStatus.ACTIVE,
                        OrderedQuantity.of(1),
                        null,
                        null);
        OrderItemRevision draft =
                OrderItemRevision.rehydrate(
                        itemId,
                        draftNumber,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(2),
                        activeNumber,
                        null);
        return OrderItem.rehydrate(
                itemId,
                orderId,
                ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                OrderItemStatus.ACTIVE,
                activeNumber,
                draftNumber,
                Map.of(activeNumber, active, draftNumber, draft),
                0L,
                NOW,
                NOW);
    }

    private static OrderItem itemWithActiveOnly(
            OrderId orderId, OrderItemId itemId, RevisionNumber activeNumber) {
        OrderItemRevision active =
                OrderItemRevision.rehydrate(
                        itemId,
                        activeNumber,
                        RevisionStatus.ACTIVE,
                        OrderedQuantity.of(1),
                        null,
                        null);
        return OrderItem.rehydrate(
                itemId,
                orderId,
                ItemCommercialData.of(ProductCode.of("P-1"), "Panel", null),
                OrderItemStatus.ACTIVE,
                activeNumber,
                null,
                Map.of(activeNumber, active),
                0L,
                NOW,
                NOW);
    }
}
