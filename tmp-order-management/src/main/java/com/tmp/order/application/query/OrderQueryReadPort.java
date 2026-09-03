package com.tmp.order.application.query;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.SpecificationId;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistRowDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only persistence port for Public Query API projections (Specification §15.1).
 *
 * <p>Must not insert, update or delete. Draft revisions and draft specifications are never
 * returned.
 */
public interface OrderQueryReadPort {

    PageResult<OrderSummaryDto> searchOrders(OrderSearchCriteria criteria, PageRequest pageRequest);

    Optional<OrderDto> findOrder(OrderId orderId);

    PageResult<OrderItemDto> findOrderItems(OrderId orderId, PageRequest pageRequest);

    Optional<OrderItemDto> findOrderItem(OrderItemId orderItemId);

    PageResult<OrderItemRevisionDto> findApprovedRevisions(
            OrderItemId orderItemId, PageRequest pageRequest);

    Optional<OrderItemRevisionDto> findApprovedRevision(
            OrderItemId orderItemId, RevisionNumber revisionNumber);

    Optional<OrderItemRevisionDto> findActiveRevision(OrderItemId orderItemId);

    Optional<ItemSpecificationDto> findApprovedSpecification(
            OrderItemId orderItemId, RevisionNumber revisionNumber);

    Optional<ProductionSpecificationDto> findCurrentSpecification(OrderItemId orderItemId);

    Optional<ProductionSpecificationDto> findSpecificationById(SpecificationId specificationId);

    List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria);

    List<OrderCustomerOptionDto> listWorklistCustomers(Instant createdFrom, Instant createdToExclusive);
}
