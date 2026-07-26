package com.tmp.order.persistence;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.repository.ItemSpecificationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Read-only JDBC adapter for {@link ItemSpecificationRepository}. Writes go only through
 * {@link JdbcOrderItemRepository#save}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcItemSpecificationRepository implements ItemSpecificationRepository {

    private final OrderItemAggregateJdbcSupport support;

    public JdbcItemSpecificationRepository(JdbcTemplate jdbcTemplate) {
        this.support = new OrderItemAggregateJdbcSupport(jdbcTemplate);
    }

    JdbcItemSpecificationRepository(OrderItemAggregateJdbcSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }

    @Override
    public Optional<ItemSpecification> findByOrderItemIdAndRevisionNumber(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return support.findSpecification(orderItemId, revisionNumber);
    }
}
