package com.tmp.order.persistence;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@link CustomerOrderRepository}. Uses optimistic locking on {@code version};
 * does not open its own transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcCustomerOrderRepository implements CustomerOrderRepository {

    private final JdbcTemplate jdbc;

    public JdbcCustomerOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public CustomerOrder save(CustomerOrder order) {
        Objects.requireNonNull(order, "order");
        Optional<CustomerOrder> existing = findById(order.id());
        if (existing.isEmpty()) {
            insert(order);
            return order;
        }
        return update(order);
    }

    @Override
    public Optional<CustomerOrder> findById(OrderId id) {
        Objects.requireNonNull(id, "id");
        List<CustomerOrder> rows =
                jdbc.query(OrderAggregateSql.SELECT_ORDER_BY_ID, (rs, rowNum) -> OrderAggregateMappers.mapOrder(rs), id.value());
        return rows.stream().findFirst();
    }

    @Override
    public boolean existsByOrderNumber(OrderNumber orderNumber) {
        Objects.requireNonNull(orderNumber, "orderNumber");
        Integer count =
                jdbc.queryForObject(
                        OrderAggregateSql.EXISTS_ORDER_BY_NUMBER, Integer.class, orderNumber.value());
        return count != null && count > 0;
    }

    private void insert(CustomerOrder order) {
        OrderCommercialData commercial = order.commercialData();
        jdbc.update(
                OrderAggregateSql.INSERT_ORDER,
                order.id().value(),
                order.orderNumber().value(),
                commercial.customerRef(),
                commercial.customerName(),
                commercial.contractRef(),
                commercial.siteRef(),
                commercial.responsibleManager(),
                commercial.direction().name(),
                commercial.currency().value(),
                order.status().name(),
                order.version(),
                OrderAggregateMappers.toTimestamp(order.createdAt()),
                OrderAggregateMappers.toTimestamp(order.updatedAt()));
    }

    private CustomerOrder update(CustomerOrder order) {
        OrderCommercialData commercial = order.commercialData();
        int updated =
                jdbc.update(
                        OrderAggregateSql.UPDATE_ORDER,
                        commercial.customerRef(),
                        commercial.customerName(),
                        commercial.contractRef(),
                        commercial.siteRef(),
                        commercial.responsibleManager(),
                        commercial.direction().name(),
                        commercial.currency().value(),
                        order.status().name(),
                        OrderAggregateMappers.toTimestamp(order.updatedAt()),
                        order.id().value(),
                        order.version());
        if (updated != 1) {
            throw new OptimisticLockConflictException(
                    "Optimistic lock conflict for order " + order.id()
                            + " expectedVersion=" + order.version());
        }
        return CustomerOrder.rehydrate(
                order.id(),
                order.orderNumber(),
                order.commercialData(),
                order.status(),
                order.version() + 1L,
                order.createdAt(),
                order.updatedAt());
    }
}
