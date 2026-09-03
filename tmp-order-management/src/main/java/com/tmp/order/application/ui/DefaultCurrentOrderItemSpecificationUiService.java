package com.tmp.order.application.ui;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationRef;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationUiService;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves current specification revision: draft if present, else active.
 */
public final class DefaultCurrentOrderItemSpecificationUiService
        implements CurrentOrderItemSpecificationUiService {

    private final OrderItemRepository orderItemRepository;
    private final AuthorizationService authorization;

    public DefaultCurrentOrderItemSpecificationUiService(
            OrderItemRepository orderItemRepository, AuthorizationService authorization) {
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public Optional<CurrentOrderItemSpecificationRef> resolveCurrent(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        authorization.requirePermission(OrderManagementPermissions.ITEM_VIEW);
        return orderItemRepository.findById(orderItemId).flatMap(this::toCurrent);
    }

    private Optional<CurrentOrderItemSpecificationRef> toCurrent(OrderItem item) {
        if (item.draftRevisionNumber().isPresent()) {
            return Optional.of(
                    CurrentOrderItemSpecificationRef.of(
                            item.id(), item.draftRevisionNumber().orElseThrow()));
        }
        return item.activeRevisionNumber()
                .map(number -> CurrentOrderItemSpecificationRef.of(item.id(), number));
    }
}
