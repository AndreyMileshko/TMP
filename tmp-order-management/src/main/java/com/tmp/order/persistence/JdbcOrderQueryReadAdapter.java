package com.tmp.order.persistence;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSort;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.SpecificationLineDto;
import com.tmp.order.application.query.OrderQueryReadPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Read-only JDBC adapter for Public Query API projections. Performs only SELECT statements.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcOrderQueryReadAdapter implements OrderQueryReadPort {

    private final JdbcTemplate jdbc;

    public JdbcOrderQueryReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public PageResult<OrderSummaryDto> searchOrders(
            OrderSearchCriteria criteria, PageRequest pageRequest) {
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(pageRequest, "pageRequest");
        FilterSql filter = buildOrderFilter(criteria);
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.orders o" + filter.whereClause,
                        Long.class,
                        filter.args.toArray());
        long totalElements = total == null ? 0L : total;
        if (totalElements == 0L) {
            return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0L);
        }
        String sql =
                """
                SELECT o.order_id, o.order_number, o.status, o.customer_ref, o.customer_name, o.created_at
                FROM order_management.orders o
                """
                        + filter.whereClause
                        + " ORDER BY "
                        + orderSortSql(pageRequest.orderSort(), "o")
                        + " LIMIT ? OFFSET ?";
        List<Object> args = new ArrayList<>(filter.args);
        args.add(pageRequest.pageSize());
        args.add((long) pageRequest.pageIndex() * pageRequest.pageSize());
        List<OrderSummaryDto> content =
                jdbc.query(sql, (rs, rowNum) -> mapOrderSummary(rs), args.toArray());
        return PageResult.of(content, pageRequest.pageIndex(), pageRequest.pageSize(), totalElements);
    }

    @Override
    public Optional<OrderDto> findOrder(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        List<OrderDto> rows =
                jdbc.query(
                        """
                        SELECT order_id, order_number, status, customer_ref, customer_name, contract_ref,
                               site_ref, responsible_manager, direction, currency, created_at, updated_at
                        FROM order_management.orders
                        WHERE order_id = ?
                        """,
                        (rs, rowNum) -> mapOrder(rs),
                        orderId.value());
        return rows.stream().findFirst();
    }

    @Override
    public PageResult<OrderItemDto> findOrderItems(OrderId orderId, PageRequest pageRequest) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        Long total =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_items WHERE order_id = ?
                        """,
                        Long.class,
                        orderId.value());
        long totalElements = total == null ? 0L : total;
        if (totalElements == 0L) {
            return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0L);
        }
        String sql =
                """
                SELECT order_item_id, order_id, product_code, item_name, comments,
                       external_position_number, status, active_revision_number, created_at,
                       updated_at
                FROM order_management.order_items
                WHERE order_id = ?
                ORDER BY created_at ASC, order_item_id ASC
                LIMIT ? OFFSET ?
                """;
        List<OrderItemDto> content =
                jdbc.query(
                        sql,
                        (rs, rowNum) -> mapOrderItem(rs),
                        orderId.value(),
                        pageRequest.pageSize(),
                        (long) pageRequest.pageIndex() * pageRequest.pageSize());
        return PageResult.of(content, pageRequest.pageIndex(), pageRequest.pageSize(), totalElements);
    }

    @Override
    public Optional<OrderItemDto> findOrderItem(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        List<OrderItemDto> rows =
                jdbc.query(
                        """
                        SELECT order_item_id, order_id, product_code, item_name, comments, status,
                               active_revision_number, created_at, updated_at
                        FROM order_management.order_items
                        WHERE order_item_id = ?
                        """,
                        (rs, rowNum) -> mapOrderItem(rs),
                        orderItemId.value());
        return rows.stream().findFirst();
    }

    @Override
    public PageResult<OrderItemRevisionDto> findApprovedRevisions(
            OrderItemId orderItemId, PageRequest pageRequest) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        Long total =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_item_revisions
                        WHERE order_item_id = ? AND revision_status = 'APPROVED'
                        """,
                        Long.class,
                        orderItemId.value());
        long totalElements = total == null ? 0L : total;
        if (totalElements == 0L) {
            return PageResult.of(List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0L);
        }
        List<OrderItemRevisionDto> content =
                jdbc.query(
                        """
                        SELECT order_item_id, revision_number, revision_status, ordered_quantity,
                               previous_revision_number
                        FROM order_management.order_item_revisions
                        WHERE order_item_id = ? AND revision_status = 'APPROVED'
                        ORDER BY revision_number ASC
                        LIMIT ? OFFSET ?
                        """,
                        (rs, rowNum) -> mapRevision(rs),
                        orderItemId.value(),
                        pageRequest.pageSize(),
                        (long) pageRequest.pageIndex() * pageRequest.pageSize());
        return PageResult.of(content, pageRequest.pageIndex(), pageRequest.pageSize(), totalElements);
    }

    @Override
    public Optional<OrderItemRevisionDto> findApprovedRevision(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        List<OrderItemRevisionDto> rows =
                jdbc.query(
                        """
                        SELECT order_item_id, revision_number, revision_status, ordered_quantity,
                               previous_revision_number
                        FROM order_management.order_item_revisions
                        WHERE order_item_id = ? AND revision_number = ?
                          AND revision_status = 'APPROVED'
                        """,
                        (rs, rowNum) -> mapRevision(rs),
                        orderItemId.value(),
                        revisionNumber.value());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<OrderItemRevisionDto> findActiveRevision(OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        List<OrderItemRevisionDto> rows =
                jdbc.query(
                        """
                        SELECT r.order_item_id, r.revision_number, r.revision_status, r.ordered_quantity,
                               r.previous_revision_number
                        FROM order_management.order_items i
                        JOIN order_management.order_item_revisions r
                          ON r.order_item_id = i.order_item_id
                         AND r.revision_number = i.active_revision_number
                        WHERE i.order_item_id = ?
                          AND i.active_revision_number IS NOT NULL
                          AND r.revision_status = 'APPROVED'
                        """,
                        (rs, rowNum) -> mapRevision(rs),
                        orderItemId.value());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ItemSpecificationDto> findApprovedSpecification(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        Integer approved =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_item_revisions
                        WHERE order_item_id = ? AND revision_number = ?
                          AND revision_status = 'APPROVED'
                        """,
                        Integer.class,
                        orderItemId.value(),
                        revisionNumber.value());
        if (approved == null || approved == 0) {
            return Optional.empty();
        }
        Integer specExists =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.item_specifications
                        WHERE order_item_id = ? AND revision_number = ?
                        """,
                        Integer.class,
                        orderItemId.value(),
                        revisionNumber.value());
        if (specExists == null || specExists == 0) {
            return Optional.empty();
        }
        List<SpecificationLineDto> lines =
                jdbc.query(
                        """
                        SELECT material_code, material_name, color, length_mm, line_quantity,
                               unit_of_measure
                        FROM order_management.item_specification_lines
                        WHERE order_item_id = ? AND revision_number = ?
                        ORDER BY line_number ASC
                        """,
                        (rs, rowNum) ->
                                SpecificationLineDto.of(
                                        rs.getString("material_code"),
                                        rs.getString("material_name"),
                                        rs.getString("color"),
                                        rs.getBigDecimal("length_mm"),
                                        rs.getBigDecimal("line_quantity"),
                                        rs.getString("unit_of_measure")),
                        orderItemId.value(),
                        revisionNumber.value());
        return Optional.of(ItemSpecificationDto.of(orderItemId, revisionNumber, lines));
    }

    private static FilterSql buildOrderFilter(OrderSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        criteria.orderNumber().ifPresent(value -> {
            where.append(" AND o.order_number = ?");
            args.add(value);
        });
        criteria.orderStatus().ifPresent(status -> {
            where.append(" AND o.status = ?");
            args.add(status.name());
        });
        criteria.customerRef().ifPresent(value -> {
            where.append(" AND o.customer_ref = ?");
            args.add(value);
        });
        criteria.customerName().ifPresent(value -> {
            where.append(" AND o.customer_name = ?");
            args.add(value);
        });
        criteria.createdFrom().ifPresent(from -> {
            where.append(" AND o.created_at >= ?");
            args.add(Timestamp.from(from));
        });
        criteria.createdTo().ifPresent(to -> {
            where.append(" AND o.created_at <= ?");
            args.add(Timestamp.from(to));
        });
        return new FilterSql(where.toString(), args);
    }

    private static String orderSortSql(OrderSort sort, String alias) {
        StringBuilder orderBy = new StringBuilder();
        List<OrderSort.Order> orders = sort.orders();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                orderBy.append(", ");
            }
            OrderSort.Order order = orders.get(i);
            orderBy.append(alias).append('.').append(orderColumn(order.field()));
            orderBy.append(' ').append(order.direction().name());
        }
        return orderBy.toString();
    }

    private static String orderColumn(OrderSort.Field field) {
        return switch (field) {
            case CREATED_AT -> "created_at";
            case ORDER_ID -> "order_id";
            case ORDER_NUMBER -> "order_number";
            case STATUS -> "status";
        };
    }

    private static OrderSummaryDto mapOrderSummary(ResultSet rs) throws SQLException {
        return OrderSummaryDto.of(
                OrderId.of(rs.getObject("order_id", UUID.class)),
                rs.getString("order_number"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("customer_ref"),
                rs.getString("customer_name"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static OrderDto mapOrder(ResultSet rs) throws SQLException {
        return OrderDto.of(
                OrderId.of(rs.getObject("order_id", UUID.class)),
                rs.getString("order_number"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("customer_ref"),
                rs.getString("customer_name"),
                rs.getString("contract_ref"),
                rs.getString("site_ref"),
                rs.getString("responsible_manager"),
                rs.getString("direction"),
                rs.getString("currency"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static OrderItemDto mapOrderItem(ResultSet rs) throws SQLException {
        Integer active = (Integer) rs.getObject("active_revision_number");
        return OrderItemDto.of(
                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                OrderId.of(rs.getObject("order_id", UUID.class)),
                rs.getString("product_code"),
                rs.getString("item_name"),
                rs.getString("comments"),
                rs.getString("external_position_number"),
                OrderItemStatus.valueOf(rs.getString("status")),
                active == null ? null : RevisionNumber.of(active),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static OrderItemRevisionDto mapRevision(ResultSet rs) throws SQLException {
        Integer previous = (Integer) rs.getObject("previous_revision_number");
        return OrderItemRevisionDto.of(
                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                RevisionNumber.of(rs.getInt("revision_number")),
                RevisionStatus.valueOf(rs.getString("revision_status")),
                rs.getBigDecimal("ordered_quantity"),
                previous == null ? null : RevisionNumber.of(previous));
    }

    private record FilterSql(String whereClause, List<Object> args) {}
}
