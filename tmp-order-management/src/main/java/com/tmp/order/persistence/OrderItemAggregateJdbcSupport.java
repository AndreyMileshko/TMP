package com.tmp.order.persistence;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.SpecificationLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared JDBC load/save helpers for the Order Item aggregate boundary. Revision and Specification
 * write paths go only through {@link #saveAggregate(OrderItem)}.
 */
final class OrderItemAggregateJdbcSupport {

    private final JdbcTemplate jdbc;

    OrderItemAggregateJdbcSupport(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    OrderItem saveAggregate(OrderItem item) {
        Objects.requireNonNull(item, "item");
        Optional<OrderItem> existing = findById(item.id());
        if (existing.isEmpty()) {
            insertHeader(item);
            replaceChildren(item);
            return item;
        }
        OrderItem persisted = updateHeader(item);
        replaceChildren(item);
        return persisted;
    }

    Optional<OrderItem> findById(OrderItemId id) {
        Objects.requireNonNull(id, "id");
        List<OrderItem> headers =
                jdbc.query(
                        OrderAggregateSql.SELECT_ORDER_ITEM_BY_ID,
                        (rs, rowNum) -> OrderAggregateMappers.mapOrderItemHeader(
                                rs, OrderAggregateMappers.emptyRevisionMap()),
                        id.value());
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rehydrateWithChildren(headers.getFirst()));
    }

    List<OrderItem> findByOrderId(com.tmp.order.api.OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        List<OrderItem> headers =
                jdbc.query(
                        OrderAggregateSql.SELECT_ORDER_ITEMS_BY_ORDER_ID,
                        (rs, rowNum) -> OrderAggregateMappers.mapOrderItemHeader(
                                rs, OrderAggregateMappers.emptyRevisionMap()),
                        orderId.value());
        List<OrderItem> result = new ArrayList<>(headers.size());
        for (OrderItem header : headers) {
            result.add(rehydrateWithChildren(header));
        }
        return List.copyOf(result);
    }

    Optional<OrderItemRevision> findRevision(OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        List<OrderItemRevision> rows =
                jdbc.query(
                        OrderAggregateSql.SELECT_REVISION_BY_KEY,
                        (rs, rowNum) -> OrderAggregateMappers.mapRevision(rs, null),
                        orderItemId.value(),
                        revisionNumber.value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        OrderItemRevision withoutSpec = rows.getFirst();
        ItemSpecification specification =
                loadSpecification(orderItemId, revisionNumber).orElse(null);
        return Optional.of(
                OrderItemRevision.rehydrate(
                        withoutSpec.orderItemId(),
                        withoutSpec.revisionNumber(),
                        withoutSpec.status(),
                        withoutSpec.orderedQuantity(),
                        withoutSpec.previousRevisionNumber().orElse(null),
                        specification));
    }

    List<OrderItemRevision> findRevisions(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        return List.copyOf(loadRevisionsMap(orderItemId).values());
    }

    Optional<ItemSpecification> findSpecification(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return loadSpecification(orderItemId, revisionNumber);
    }

    private OrderItem rehydrateWithChildren(OrderItem header) {
        Map<RevisionNumber, OrderItemRevision> revisions = loadRevisionsMap(header.id());
        return OrderItem.rehydrate(
                header.id(),
                header.orderId(),
                header.commercialData(),
                header.status(),
                header.activeRevisionNumber().orElse(null),
                header.draftRevisionNumber().orElse(null),
                revisions,
                header.version(),
                header.createdAt(),
                header.updatedAt());
    }

    private Map<RevisionNumber, OrderItemRevision> loadRevisionsMap(OrderItemId orderItemId) {
        List<OrderItemRevision> withoutSpecs =
                jdbc.query(
                        OrderAggregateSql.SELECT_REVISIONS_BY_ITEM,
                        (rs, rowNum) -> OrderAggregateMappers.mapRevision(rs, null),
                        orderItemId.value());
        Map<RevisionNumber, OrderItemRevision> result = new LinkedHashMap<>();
        for (OrderItemRevision revision : withoutSpecs) {
            ItemSpecification specification =
                    loadSpecification(orderItemId, revision.revisionNumber()).orElse(null);
            result.put(
                    revision.revisionNumber(),
                    OrderItemRevision.rehydrate(
                            revision.orderItemId(),
                            revision.revisionNumber(),
                            revision.status(),
                            revision.orderedQuantity(),
                            revision.previousRevisionNumber().orElse(null),
                            specification));
        }
        return result;
    }

    private Optional<ItemSpecification> loadSpecification(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        List<ItemSpecification> headers =
                jdbc.query(
                        OrderAggregateSql.SELECT_SPEC_BY_KEY,
                        (rs, rowNum) -> OrderAggregateMappers.mapSpecification(rs, List.of()),
                        orderItemId.value(),
                        revisionNumber.value());
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        List<SpecificationLine> lines =
                jdbc.query(
                        OrderAggregateSql.SELECT_SPEC_LINES_BY_KEY,
                        (rs, rowNum) -> OrderAggregateMappers.mapSpecLine(rs),
                        orderItemId.value(),
                        revisionNumber.value());
        ItemSpecification header = headers.getFirst();
        return Optional.of(
                ItemSpecification.rehydrate(
                        header.orderItemId(),
                        header.revisionNumber(),
                        lines,
                        header.isImmutable()));
    }

    private void insertHeader(OrderItem item) {
        jdbc.update(
                OrderAggregateSql.INSERT_ORDER_ITEM,
                item.id().value(),
                item.orderId().value(),
                item.commercialData().productCode().value(),
                item.commercialData().name(),
                item.commercialData().comments(),
                item.commercialData().externalPositionNumber(),
                item.status().name(),
                item.activeRevisionNumber().map(RevisionNumber::value).orElse(null),
                item.draftRevisionNumber().map(RevisionNumber::value).orElse(null),
                item.version(),
                OrderAggregateMappers.toTimestamp(item.createdAt()),
                OrderAggregateMappers.toTimestamp(item.updatedAt()));
    }

    private OrderItem updateHeader(OrderItem item) {
        int updated =
                jdbc.update(
                        OrderAggregateSql.UPDATE_ORDER_ITEM,
                        item.commercialData().productCode().value(),
                        item.commercialData().name(),
                        item.commercialData().comments(),
                        item.commercialData().externalPositionNumber(),
                        item.status().name(),
                        item.activeRevisionNumber().map(RevisionNumber::value).orElse(null),
                        item.draftRevisionNumber().map(RevisionNumber::value).orElse(null),
                        OrderAggregateMappers.toTimestamp(item.updatedAt()),
                        item.id().value(),
                        item.version());
        if (updated != 1) {
            throw new OptimisticLockConflictException(
                    "Optimistic lock conflict for order item " + item.id()
                            + " expectedVersion=" + item.version());
        }
        return OrderItem.rehydrate(
                item.id(),
                item.orderId(),
                item.commercialData(),
                item.status(),
                item.activeRevisionNumber().orElse(null),
                item.draftRevisionNumber().orElse(null),
                item.revisions(),
                item.version() + 1L,
                item.createdAt(),
                item.updatedAt());
    }

    private void replaceChildren(OrderItem item) {
        jdbc.update(OrderAggregateSql.DELETE_SPEC_LINES_FOR_ITEM, item.id().value());
        jdbc.update(OrderAggregateSql.DELETE_SPECS_FOR_ITEM, item.id().value());
        jdbc.update(OrderAggregateSql.DELETE_REVISIONS_FOR_ITEM, item.id().value());
        for (OrderItemRevision revision : item.revisions().values()) {
            jdbc.update(
                    OrderAggregateSql.INSERT_REVISION,
                    revision.orderItemId().value(),
                    revision.revisionNumber().value(),
                    revision.status().name(),
                    OrderAggregateMappers.quantityValue(revision.orderedQuantity()),
                    revision.previousRevisionNumber().map(RevisionNumber::value).orElse(null));
            revision.specification().ifPresent(spec -> insertSpecification(spec));
        }
    }

    private void insertSpecification(ItemSpecification specification) {
        jdbc.update(
                OrderAggregateSql.INSERT_SPECIFICATION,
                specification.orderItemId().value(),
                specification.revisionNumber().value(),
                specification.isImmutable());
        int lineNumber = 1;
        for (SpecificationLine line : specification.lines()) {
            jdbc.update(
                    OrderAggregateSql.INSERT_SPEC_LINE,
                    specification.orderItemId().value(),
                    specification.revisionNumber().value(),
                    lineNumber,
                    line.materialCode(),
                    line.materialName(),
                    line.color(),
                    line.lengthMm(),
                    line.lineQuantity(),
                    line.unitOfMeasure());
            lineNumber++;
        }
    }
}
