package com.tmp.order.persistence;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ResultSet mappers for Order Management aggregates. Enum values use stable {@code name()} codes.
 */
final class OrderAggregateMappers {

    private OrderAggregateMappers() {}

    static CustomerOrder mapOrder(ResultSet rs) throws SQLException {
        return CustomerOrder.rehydrate(
                OrderId.of(rs.getObject("order_id", UUID.class)),
                OrderNumber.of(rs.getString("order_number")),
                mapOrderCommercial(rs),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    static OrderCommercialData mapOrderCommercial(ResultSet rs) throws SQLException {
        String directionCode = rs.getString("direction");
        String currencyCode = rs.getString("currency");
        return OrderCommercialData.of(
                rs.getString("customer_ref"),
                rs.getString("customer_name"),
                rs.getString("contract_ref"),
                rs.getString("site_ref"),
                rs.getString("responsible_manager"),
                directionCode == null ? null : OrderDirection.valueOf(directionCode),
                currencyCode == null ? null : CurrencyCode.of(currencyCode));
    }

    static OrderItem mapOrderItemHeader(
            ResultSet rs, Map<RevisionNumber, OrderItemRevision> revisions) throws SQLException {
        Integer active = (Integer) rs.getObject("active_revision_number");
        Integer draft = (Integer) rs.getObject("draft_revision_number");
        return OrderItem.rehydrate(
                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                OrderId.of(rs.getObject("order_id", UUID.class)),
                ItemCommercialData.of(
                        ProductCode.of(rs.getString("product_code")),
                        rs.getString("item_name"),
                        rs.getString("comments"),
                        rs.getString("external_position_number")),
                OrderItemStatus.valueOf(rs.getString("status")),
                active == null ? null : RevisionNumber.of(active),
                draft == null ? null : RevisionNumber.of(draft),
                revisions,
                rs.getLong("version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    static OrderItemRevision mapRevision(
            ResultSet rs, ItemSpecification specification) throws SQLException {
        Integer previous = (Integer) rs.getObject("previous_revision_number");
        return OrderItemRevision.rehydrate(
                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                RevisionNumber.of(rs.getInt("revision_number")),
                RevisionStatus.valueOf(rs.getString("revision_status")),
                OrderedQuantity.of(rs.getBigDecimal("ordered_quantity")),
                previous == null ? null : RevisionNumber.of(previous),
                specification);
    }

    static ItemSpecification mapSpecification(
            ResultSet rs, List<SpecificationLine> lines) throws SQLException {
        return ItemSpecification.rehydrate(
                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                RevisionNumber.of(rs.getInt("revision_number")),
                lines,
                rs.getBoolean("immutable"));
    }

    static SpecificationLine mapSpecLine(ResultSet rs) throws SQLException {
        return SpecificationLine.of(
                rs.getString("material_code"),
                rs.getString("material_name"),
                rs.getString("color"),
                rs.getBigDecimal("length_mm"),
                rs.getBigDecimal("line_quantity"),
                rs.getString("unit_of_measure"));
    }

    static Map<RevisionNumber, OrderItemRevision> emptyRevisionMap() {
        return new LinkedHashMap<>();
    }

    static Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    static Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    static BigDecimal quantityValue(OrderedQuantity quantity) {
        return quantity.value();
    }
}
