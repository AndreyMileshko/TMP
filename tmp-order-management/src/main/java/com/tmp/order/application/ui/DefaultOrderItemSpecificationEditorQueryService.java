package com.tmp.order.application.ui;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.security.api.AuthorizationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads Draft or Approved Specification snapshots for the desktop Specification editor only.
 */
public final class DefaultOrderItemSpecificationEditorQueryService
        implements OrderItemSpecificationEditorQueryService {

    private final OrderItemRepository orderItemRepository;
    private final AuthorizationService authorization;

    public DefaultOrderItemSpecificationEditorQueryService(
            OrderItemRepository orderItemRepository, AuthorizationService authorization) {
        this.orderItemRepository =
                Objects.requireNonNull(orderItemRepository, "orderItemRepository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public Optional<OrderItemSpecificationEditorSnapshot> getSpecificationSnapshot(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        authorization.requirePermission(OrderManagementPermissions.SPECIFICATION_VIEW);
        return orderItemRepository
                .findById(orderItemId)
                .flatMap(item -> toSnapshot(item, revisionNumber));
    }

    private Optional<OrderItemSpecificationEditorSnapshot> toSnapshot(
            OrderItem item, RevisionNumber revisionNumber) {
        Optional<OrderItemRevision> revision = item.revision(revisionNumber);
        if (revision.isEmpty()) {
            return Optional.empty();
        }
        OrderItemRevision found = revision.get();
        ItemSpecification specification =
                found.specification()
                        .orElse(ItemSpecification.empty(item.id(), revisionNumber));
        List<OrderItemSpecificationLineView> lines = new ArrayList<>();
        int lineNumber = 1;
        for (SpecificationLine line : specification.lines()) {
            lines.add(
                    OrderItemSpecificationLineView.of(
                            lineNumber++,
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.lengthMm(),
                            line.lineQuantity(),
                            line.unitOfMeasure()));
        }
        boolean immutable = found.status() == RevisionStatus.ACTIVE || specification.isImmutable();
        return Optional.of(
                OrderItemSpecificationEditorSnapshot.of(
                        item.id(),
                        found.revisionNumber(),
                        found.status(),
                        UiProductQuantityNormalization.forUiContract(
                                found.orderedQuantity().value()),
                        immutable,
                        lines));
    }
}
