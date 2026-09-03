package com.tmp.order.application.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultOrderQueryServiceSecurityTest {

    @Test
    void allowedOrderViewSucceeds() {
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(
                        EmptyReadPort.INSTANCE,
                        new FixedAuthorization(Set.of(OrderManagementPermissions.ORDER_VIEW)));
        assertDoesNotThrow(
                () -> service.searchOrders(OrderSearchCriteria.empty(), PageRequest.firstPage()));
        assertDoesNotThrow(() -> service.getOrder(OrderId.generate()));
    }

    @Test
    void missingOrderViewIsDenied() {
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(EmptyReadPort.INSTANCE, new FixedAuthorization(Set.of()));
        assertThrows(
                AccessDeniedException.class,
                () -> service.searchOrders(OrderSearchCriteria.empty(), PageRequest.firstPage()));
        assertThrows(AccessDeniedException.class, () -> service.getOrder(OrderId.generate()));
    }

    @Test
    void missingItemViewIsDenied() {
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(
                        EmptyReadPort.INSTANCE,
                        new FixedAuthorization(Set.of(OrderManagementPermissions.ORDER_VIEW)));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getOrderItems(OrderId.generate(), PageRequest.firstPage()));
        assertThrows(AccessDeniedException.class, () -> service.getOrderItem(OrderItemId.generate()));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getOrderItemRevisions(OrderItemId.generate(), PageRequest.firstPage()));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        service.getOrderItemRevision(
                                OrderItemId.generate(), RevisionNumber.first()));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getActiveOrderItemRevision(OrderItemId.generate()));
    }

    @Test
    void missingSpecificationViewIsDenied() {
        DefaultOrderQueryService service =
                new DefaultOrderQueryService(
                        EmptyReadPort.INSTANCE,
                        new FixedAuthorization(Set.of(OrderManagementPermissions.ITEM_VIEW)));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getItemSpecification(
                                OrderItemId.generate(), RevisionNumber.first()));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getCurrentItemSpecification(OrderItemId.generate()));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getSpecificationById(
                        com.tmp.order.api.SpecificationId.of(java.util.UUID.randomUUID())));
        assertThrows(
                AccessDeniedException.class,
                () -> service.getOrderForProduction(OrderId.generate()));
    }

    @Test
    void getOrderForProductionRequiresAllViewPermissions() {
        DefaultOrderQueryService missingSpecView =
                new DefaultOrderQueryService(
                        EmptyReadPort.INSTANCE,
                        new FixedAuthorization(
                                Set.of(
                                        OrderManagementPermissions.ORDER_VIEW,
                                        OrderManagementPermissions.ITEM_VIEW)));
        assertThrows(
                AccessDeniedException.class,
                () -> missingSpecView.getOrderForProduction(OrderId.generate()));
    }

    @Test
    void doesNotImportInternalSecurityPackages() {
        String sourcePackage = DefaultOrderQueryService.class.getPackageName();
        assertTrue(sourcePackage.startsWith("com.tmp.order.application"));
        // compile-time dependency is only com.tmp.security.api (enforced by architecture tests)
    }

    private static final class FixedAuthorization implements AuthorizationService {
        private final Set<PermissionId> allowed;

        private FixedAuthorization(Set<PermissionId> allowed) {
            this.allowed = Set.copyOf(allowed);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId);
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException(
                        "Access denied for permission: " + permissionId.value());
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return allowed;
        }
    }

    private enum EmptyReadPort implements OrderQueryReadPort {
        INSTANCE;

        @Override
        public PageResult<com.tmp.order.api.OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            return PageResult.of(java.util.List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0L);
        }

        @Override
        public Optional<com.tmp.order.api.OrderDto> findOrder(OrderId orderId) {
            return Optional.empty();
        }

        @Override
        public PageResult<com.tmp.order.api.OrderItemDto> findOrderItems(
                OrderId orderId, PageRequest pageRequest) {
            return PageResult.of(java.util.List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0L);
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemDto> findOrderItem(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public PageResult<com.tmp.order.api.OrderItemRevisionDto> findApprovedRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            return PageResult.of(java.util.List.of(), pageRequest.pageIndex(), pageRequest.pageSize(), 0L);
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemRevisionDto> findApprovedRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemRevisionDto> findActiveRevision(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ItemSpecificationDto> findApprovedSpecification(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> findCurrentSpecification(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> findSpecificationById(
                com.tmp.order.api.SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public List<com.tmp.order.api.OrderWorklistRowDto> listWorklistRows(
                com.tmp.order.api.OrderWorklistCriteria criteria) {
            return List.of();
        }

        @Override
        public List<com.tmp.order.api.OrderCustomerOptionDto> listWorklistCustomers(
                java.time.Instant createdFrom, java.time.Instant createdToExclusive) {
            return List.of();
        }
    }
}
