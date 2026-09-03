package com.tmp.order.persistence;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderCustomerOptionDto;
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
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.SpecificationId;
import com.tmp.order.api.SpecificationLineDto;
import com.tmp.order.application.query.OrderQueryReadPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
                        SELECT order_item_id, order_id, product_code, item_name, comments,
                               external_position_number, status, active_revision_number, created_at,
                               updated_at
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
                        WHERE order_item_id = ? AND revision_status = 'ACTIVE'
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
                        WHERE order_item_id = ? AND revision_status = 'ACTIVE'
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
                          AND revision_status = 'ACTIVE'
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
                          AND r.revision_status = 'ACTIVE'
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
                          AND revision_status = 'ACTIVE'
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

    @Override
    public Optional<ProductionSpecificationDto> findCurrentSpecification(
            OrderItemId orderItemId) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        List<ProductionSpecificationDto> rows =
                jdbc.query(
                        """
                        SELECT s.specification_id, s.order_item_id, r.ordered_quantity
                        FROM order_management.order_items i
                        JOIN order_management.order_item_revisions r
                          ON r.order_item_id = i.order_item_id
                         AND r.revision_number = i.active_revision_number
                        JOIN order_management.item_specifications s
                          ON s.order_item_id = r.order_item_id
                         AND s.revision_number = r.revision_number
                        WHERE i.order_item_id = ?
                          AND i.active_revision_number IS NOT NULL
                          AND r.revision_status = 'ACTIVE'
                        """,
                        (rs, rowNum) -> {
                            SpecificationId specId =
                                    SpecificationId.of(rs.getObject("specification_id", UUID.class));
                            OrderItemId itemId =
                                    OrderItemId.of(rs.getObject("order_item_id", UUID.class));
                            return ProductionSpecificationDto.of(
                                    specId,
                                    itemId,
                                    rs.getBigDecimal("ordered_quantity"),
                                    loadSpecLinesBySpecIdentity(itemId, specId));
                        },
                        orderItemId.value());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ProductionSpecificationDto> findSpecificationById(
            SpecificationId specificationId) {
        Objects.requireNonNull(specificationId, "specificationId");
        List<ProductionSpecificationDto> rows =
                jdbc.query(
                        """
                        SELECT s.specification_id, s.order_item_id, s.revision_number,
                               r.ordered_quantity
                        FROM order_management.item_specifications s
                        JOIN order_management.order_item_revisions r
                          ON r.order_item_id = s.order_item_id
                         AND r.revision_number = s.revision_number
                        WHERE s.specification_id = ?
                          AND r.revision_status = 'ACTIVE'
                        """,
                        (rs, rowNum) -> {
                            SpecificationId specId =
                                    SpecificationId.of(rs.getObject("specification_id", UUID.class));
                            OrderItemId itemId =
                                    OrderItemId.of(rs.getObject("order_item_id", UUID.class));
                            RevisionNumber revNum =
                                    RevisionNumber.of(rs.getInt("revision_number"));
                            List<SpecificationLineDto> lines = loadSpecLines(itemId, revNum);
                            return ProductionSpecificationDto.of(
                                    specId, itemId, rs.getBigDecimal("ordered_quantity"), lines);
                        },
                        specificationId.value());
        return rows.stream().findFirst();
    }

    private List<SpecificationLineDto> loadSpecLinesBySpecIdentity(
            OrderItemId orderItemId, SpecificationId specificationId) {
        Integer revNum =
                jdbc.queryForObject(
                        """
                        SELECT revision_number FROM order_management.item_specifications
                        WHERE order_item_id = ? AND specification_id = ?
                        """,
                        Integer.class,
                        orderItemId.value(),
                        specificationId.value());
        if (revNum == null) {
            return List.of();
        }
        return loadSpecLines(orderItemId, RevisionNumber.of(revNum));
    }

    private List<SpecificationLineDto> loadSpecLines(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return jdbc.query(
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
    }

    @Override
    public List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        FilterSql filter = buildWorklistFilter(criteria);
        String sql =
                """
                SELECT o.order_id, o.order_number, o.status, o.customer_ref, o.customer_name,
                       o.created_at, COALESCE(q.item_quantity, 0) AS item_quantity
                  FROM order_management.orders o
                  LEFT JOIN (
                        SELECT i.order_id,
                               COALESCE(SUM(qty.ordered_quantity), 0) AS item_quantity
                          FROM order_management.order_items i
                          LEFT JOIN LATERAL (
                                SELECT r.ordered_quantity
                                  FROM order_management.order_item_revisions r
                                 WHERE r.order_item_id = i.order_item_id
                                 ORDER BY CASE
                                            WHEN i.active_revision_number IS NOT NULL
                                             AND r.revision_number = i.active_revision_number
                                            THEN 0 ELSE 1
                                          END,
                                          r.revision_number DESC
                                 LIMIT 1
                          ) qty ON TRUE
                         GROUP BY i.order_id
                  ) q ON q.order_id = o.order_id
                """
                        + filter.whereClause
                        + """
                 ORDER BY o.created_at DESC, o.order_id DESC
                 LIMIT ?
                """;
        List<Object> args = new ArrayList<>(filter.args);
        args.add(OrderWorklistCriteria.MAX_ROWS + 1);
        return jdbc.query(sql, (rs, rowNum) -> mapWorklistRow(rs), args.toArray());
    }

    @Override
    public List<OrderCustomerOptionDto> listWorklistCustomers(
            Instant createdFrom, Instant createdToExclusive) {
        Objects.requireNonNull(createdFrom, "createdFrom");
        Objects.requireNonNull(createdToExclusive, "createdToExclusive");
        if (!createdFrom.isBefore(createdToExclusive)) {
            throw new IllegalArgumentException(
                    "createdFrom must be before createdToExclusive: "
                            + createdFrom
                            + " / "
                            + createdToExclusive);
        }
        List<OrderCustomerOptionDto> options =
                jdbc.query(
                        """
                        SELECT DISTINCT o.customer_ref, o.customer_name
                          FROM order_management.orders o
                         WHERE o.created_at >= ?
                           AND o.created_at < ?
                           AND o.customer_ref IS NOT NULL
                         ORDER BY o.customer_name NULLS LAST, o.customer_ref
                        """,
                        (rs, rowNum) ->
                                OrderCustomerOptionDto.of(
                                        rs.getString("customer_ref"), rs.getString("customer_name")),
                        Timestamp.from(createdFrom),
                        Timestamp.from(createdToExclusive));
        Integer unassigned =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                          FROM order_management.orders o
                         WHERE o.created_at >= ?
                           AND o.created_at < ?
                           AND o.customer_ref IS NULL
                        """,
                        Integer.class,
                        Timestamp.from(createdFrom),
                        Timestamp.from(createdToExclusive));
        List<OrderCustomerOptionDto> result = new ArrayList<>(options);
        if (unassigned != null && unassigned > 0) {
            result.add(0, OrderCustomerOptionDto.unassigned());
        }
        return List.copyOf(result);
    }

    private static FilterSql buildWorklistFilter(OrderWorklistCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE o.created_at >= ? AND o.created_at < ?");
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(criteria.createdFrom()));
        args.add(Timestamp.from(criteria.createdToExclusive()));
        criteria.quickSearch().ifPresent(value -> {
            String pattern = '%' + escapeIlike(value) + '%';
            where.append(" AND (o.order_number ILIKE ? ESCAPE '\\' OR o.customer_name ILIKE ? ESCAPE '\\')");
            args.add(pattern);
            args.add(pattern);
        });
        if (criteria.filterByCustomers()) {
            Set<String> refs = criteria.customerRefs();
            boolean unassigned = criteria.includeUnassignedCustomer();
            if (refs.isEmpty() && !unassigned) {
                where.append(" AND 1=0");
            } else if (refs.isEmpty()) {
                where.append(" AND o.customer_ref IS NULL");
            } else {
                where.append(" AND (o.customer_ref IN (");
                boolean first = true;
                for (String ref : refs) {
                    if (!first) {
                        where.append(", ");
                    }
                    first = false;
                    where.append('?');
                    args.add(ref);
                }
                where.append(')');
                if (unassigned) {
                    where.append(" OR o.customer_ref IS NULL");
                }
                where.append(')');
            }
        }
        return new FilterSql(where.toString(), args);
    }

    private static String escapeIlike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static OrderWorklistRowDto mapWorklistRow(ResultSet rs) throws SQLException {
        BigDecimal quantity = rs.getBigDecimal("item_quantity");
        long itemQuantity = quantity == null ? 0L : quantity.longValueExact();
        return OrderWorklistRowDto.of(
                OrderId.of(rs.getObject("order_id", UUID.class)),
                rs.getString("order_number"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("customer_ref"),
                rs.getString("customer_name"),
                rs.getTimestamp("created_at").toInstant(),
                itemQuantity);
    }

    private record FilterSql(String whereClause, List<Object> args) {}
}
