package com.tmp.order.persistence;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.repository.OrderItemRevisionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Read-only JDBC adapter for {@link OrderItemRevisionRepository}. Writes go only through
 * {@link JdbcOrderItemRepository#save}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcOrderItemRevisionRepository implements OrderItemRevisionRepository {

    private final OrderItemAggregateJdbcSupport support;

    public JdbcOrderItemRevisionRepository(JdbcTemplate jdbcTemplate) {
        this.support = new OrderItemAggregateJdbcSupport(jdbcTemplate);
    }

    JdbcOrderItemRevisionRepository(OrderItemAggregateJdbcSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }

    @Override
    public Optional<OrderItemRevision> findByOrderItemIdAndRevisionNumber(
            OrderItemId orderItemId, RevisionNumber revisionNumber) {
        return support.findRevision(orderItemId, revisionNumber);
    }

    @Override
    public List<OrderItemRevision> findByOrderItemId(OrderItemId orderItemId) {
        return support.findRevisions(orderItemId);
    }
}
