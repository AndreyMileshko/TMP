package com.tmp.order.application.ui;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads {@link OrderItemEditorSnapshot} including optional Draft Revision for desktop UI only.
 */
public final class DefaultOrderItemEditorQueryService implements OrderItemEditorQueryService {

    private final OrderItemRepository orderItemRepository;
    private final OrderQueryService orderQueryService;
    private final AuthorizationService authorization;

    public DefaultOrderItemEditorQueryService(
            OrderItemRepository orderItemRepository,
            OrderQueryService orderQueryService,
            AuthorizationService authorization) {
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public Optional<OrderItemEditorSnapshot> getEditorSnapshot(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return orderItemRepository.findById(orderItemId).map(this::toSnapshot);
    }

    private OrderItemEditorSnapshot toSnapshot(OrderItem item) {
        ItemCommercialData commercial = item.commercialData();
        OrderItemEditorSnapshot.RevisionView active =
                item.activeRevision().map(this::toRevisionView).orElse(null);
        OrderItemEditorSnapshot.RevisionView draft =
                item.draftRevision().map(this::toRevisionView).orElse(null);
        BigDecimal orderedQuantity =
                draft != null
                        ? draft.orderedQuantity()
                        : active != null ? active.orderedQuantity() : BigDecimal.ONE;
        String externalPositionNumber =
                orderQueryService
                        .getOrderItem(item.id())
                        .map(dto -> dto.externalPositionNumber())
                        .orElse(commercial.externalPositionNumber());
        return OrderItemEditorSnapshot.of(
                item.id(),
                item.orderId(),
                commercial.productCode() == null ? null : commercial.productCode().value(),
                commercial.name(),
                commercial.comments(),
                externalPositionNumber,
                item.status(),
                active,
                draft,
                orderedQuantity);
    }

    private OrderItemEditorSnapshot.RevisionView toRevisionView(OrderItemRevision revision) {
        int lineCount =
                revision
                        .specification()
                        .map(ItemSpecification::lines)
                        .map(java.util.List::size)
                        .orElse(0);
        return OrderItemEditorSnapshot.RevisionView.of(
                revision.revisionNumber(),
                revision.status(),
                revision.orderedQuantity().value(),
                lineCount);
    }
}
