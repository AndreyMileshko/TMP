package com.tmp.order.persistence;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.repository.OrderItemRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@link OrderItemRepository}. Saves the Order Item aggregate atomically
 * (item + revisions + specifications + lines + active/draft pointers) within the caller's
 * transaction.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcOrderItemRepository implements OrderItemRepository {

    private final OrderItemAggregateJdbcSupport support;

    public JdbcOrderItemRepository(JdbcTemplate jdbcTemplate) {
        this.support = new OrderItemAggregateJdbcSupport(jdbcTemplate);
    }

    @Override
    public OrderItem save(OrderItem item) {
        return support.saveAggregate(item);
    }

    @Override
    public Optional<OrderItem> findById(OrderItemId id) {
        return support.findById(id);
    }

    @Override
    public List<OrderItem> findByOrderId(OrderId orderId) {
        return support.findByOrderId(orderId);
    }

    /**
     * Package-visible constructor for tests that inject a shared support instance.
     */
    JdbcOrderItemRepository(OrderItemAggregateJdbcSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }
}
