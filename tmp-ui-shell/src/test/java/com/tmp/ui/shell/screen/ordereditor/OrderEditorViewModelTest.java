package com.tmp.ui.shell.screen.ordereditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderEditorViewModelTest {

    @Test
    void openCreateEnablesSaveWithoutDocumentButtons() {
        FakeDocs docs = new FakeDocs();
        OrderEditorViewModel viewModel = vm(new FakeQuery(), docs, allOrderPerms());
        viewModel.openCreate();
        assertEquals(OrderEditorViewModel.Mode.CREATE, viewModel.modeProperty().get());
        assertEquals("Новый заказ", viewModel.titleProperty().get());
        assertTrue(viewModel.canSaveProperty().get());
        assertFalse(viewModel.canTransferToWorkProperty().get());
        assertEquals(OrderOperationalStatus.EDITING, viewModel.operationalStatus());
    }

    @Test
    void saveNewOrderCreatesEditableDraftInOneAction() {
        FakeDocs docs = new FakeDocs();
        FakeQuery query = new FakeQuery();
        OrderId created = OrderId.generate();
        docs.saveNewResult = created;
        query.order = order(created, OrderStatus.DRAFT);
        OrderEditorViewModel viewModel = vm(query, docs, allOrderPerms());
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("A-100");
        viewModel.customerNameProperty().set("Acme");
        viewModel.save();
        assertTrue(docs.saveNewCalled);
        assertFalse(docs.beginCreateCalled);
        assertEquals(created, viewModel.orderIdForTest());
        assertEquals(OrderEditorViewModel.Mode.VIEW_EXISTING, viewModel.modeProperty().get());
        assertTrue(viewModel.canSaveProperty().get());
        assertTrue(viewModel.canOpenItemsProperty().get());
        assertTrue(viewModel.canTransferToWorkProperty().get());
        assertEquals("Заказ сохранён", viewModel.successMessageProperty().get());
    }

    @Test
    void saveExistingDraftRemainsEditable() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.DRAFT);
        FakeDocs docs = new FakeDocs();
        OrderEditorViewModel viewModel = vm(query, docs, allOrderPerms());
        viewModel.openExisting(id);
        viewModel.customerNameProperty().set("Updated");
        viewModel.save();
        assertTrue(docs.saveExistingCalled);
        assertTrue(viewModel.fieldsEditableProperty().get());
        assertEquals(OrderStatus.DRAFT, viewModel.currentOrderStatus());
    }

    @Test
    void transferToWorkIsOneApplicationCall() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.DRAFT);
        FakeDocs docs = new FakeDocs();
        docs.afterTransfer = () -> query.order = order(id, OrderStatus.ACTIVE);
        OrderEditorViewModel viewModel = vm(query, docs, allOrderPerms());
        viewModel.openExisting(id);
        viewModel.transferToWork();
        assertTrue(docs.transferCalled);
        assertEquals(1, docs.transferCalls);
        assertFalse(viewModel.fieldsEditableProperty().get());
        assertFalse(viewModel.canSaveProperty().get());
        assertFalse(viewModel.canTransferToWorkProperty().get());
        assertEquals(OrderOperationalStatus.AWAITING_PRODUCTION, viewModel.operationalStatus());
        assertTrue(viewModel.transferConfirmationTitle().contains("N-1"));
        assertTrue(OrderEditorViewModel.TRANSFER_IMMUTABILITY_HINT.contains("нельзя будет редактировать"));
    }

    @Test
    void approvedCanStillTransferToWork() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.APPROVED);
        FakeDocs docs = new FakeDocs();
        docs.afterTransfer = () -> query.order = order(id, OrderStatus.ACTIVE);
        OrderEditorViewModel viewModel = vm(query, docs, allOrderPerms());
        viewModel.openExisting(id);
        assertEquals(OrderOperationalStatus.EDITING, viewModel.operationalStatus());
        assertFalse(viewModel.fieldsEditableProperty().get());
        assertTrue(viewModel.canTransferToWorkProperty().get());
        viewModel.transferToWork();
        assertTrue(docs.transferCalled);
        assertFalse(viewModel.canTransferToWorkProperty().get());
    }

    @Test
    void activeAndCancelledAreReadOnly() {
        OrderId activeId = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(activeId, OrderStatus.ACTIVE);
        OrderEditorViewModel viewModel = vm(query, new FakeDocs(), allOrderPerms());
        viewModel.openExisting(activeId);
        assertFalse(viewModel.fieldsEditableProperty().get());
        assertFalse(viewModel.canSaveProperty().get());
        assertFalse(viewModel.canCancelProperty().get());

        OrderId cancelledId = OrderId.generate();
        query.order = order(cancelledId, OrderStatus.CANCELLED);
        viewModel.openExisting(cancelledId);
        assertFalse(viewModel.canCancelProperty().get());
        assertEquals(OrderOperationalStatus.CANCELLED, viewModel.operationalStatus());
    }

    @Test
    void permissionsHideUnavailableActions() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                vm(query, new FakeDocs(), Set.of(PermissionId.of("order.order.view")));
        viewModel.openExisting(id);
        assertFalse(viewModel.canSaveProperty().get());
        assertFalse(viewModel.canTransferToWorkProperty().get());
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void viewModelHasNoRepositoryOrJdbcFields() throws Exception {
        for (Field field : OrderEditorViewModel.class.getDeclaredFields()) {
            String name = field.getType().getName();
            assertFalse(name.contains("JdbcTemplate"));
            assertFalse(name.contains("Repository"));
            assertFalse(name.contains("persistence"));
            assertFalse(name.contains("DocumentProcessor"));
        }
    }

    @Test
    void productionAccessDeniedShowsUnavailableNotFakeAwaiting() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.ACTIVE);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, new FakeDocs(), auth(allOrderPerms()), new DeniedProductionQuery());
        viewModel.openExisting(id);
        assertEquals(OrderOperationalStatus.STATUS_UNAVAILABLE, viewModel.operationalStatus());
        assertEquals("Статус недоступен", viewModel.statusTextProperty().get());
    }

    @Test
    void productionTechnicalFailureShowsUnavailableNotFakeZeros() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.ACTIVE);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(
                        query, new FakeDocs(), auth(allOrderPerms()), new FailingProductionQuery());
        viewModel.openExisting(id);
        assertEquals(OrderOperationalStatus.STATUS_UNAVAILABLE, viewModel.operationalStatus());
        assertFalse(viewModel.errorMessageProperty().get().isBlank());
    }

    private static OrderEditorViewModel vm(
            OrderQueryService query, OrderDocumentUiService docs, Set<PermissionId> perms) {
        return new OrderEditorViewModel(query, docs, auth(perms), new EmptyProductionQuery());
    }

    private static final class DeniedProductionQuery implements com.tmp.production.api.ProductionQueryApi {
        @Override
        public OrderProductionView getOrderProductionView(java.util.UUID orderId) {
            throw new com.tmp.security.api.AccessDeniedException("production.order.view");
        }

        @Override
        public java.util.Map<java.util.UUID, OrderProductionListFacts> getOrderProductionListFacts(
                java.util.Collection<java.util.UUID> orderIds) {
            throw new com.tmp.security.api.AccessDeniedException("production.order.view");
        }

        @Override
        public java.util.Optional<ItemProductionStateView> getItemProductionState(
                java.util.UUID orderItemId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(
                java.util.UUID orderId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<ProductionHistoryEntryView> listProductionHistory(java.util.UUID orderId) {
            return java.util.List.of();
        }
    }

    private static final class FailingProductionQuery implements com.tmp.production.api.ProductionQueryApi {
        @Override
        public OrderProductionView getOrderProductionView(java.util.UUID orderId) {
            throw new IllegalStateException("production exploded");
        }

        @Override
        public java.util.Map<java.util.UUID, OrderProductionListFacts> getOrderProductionListFacts(
                java.util.Collection<java.util.UUID> orderIds) {
            throw new IllegalStateException("production exploded");
        }

        @Override
        public java.util.Optional<ItemProductionStateView> getItemProductionState(
                java.util.UUID orderItemId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(
                java.util.UUID orderId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<ProductionHistoryEntryView> listProductionHistory(java.util.UUID orderId) {
            return java.util.List.of();
        }
    }

    private static Set<PermissionId> allOrderPerms() {
        return Set.of(
                PermissionId.of("order.order.view"),
                PermissionId.of("order.order.create"),
                PermissionId.of("order.order.edit"),
                PermissionId.of("order.order.approve"),
                PermissionId.of("order.order.cancel"),
                PermissionId.of("order.item.view"));
    }

    private static AuthorizationService auth(Set<PermissionId> granted) {
        return new FakeAuthorization(granted);
    }

    private static OrderDto order(OrderId id, OrderStatus status) {
        return OrderDto.of(
                id,
                "N-1",
                status,
                "cref",
                "Customer",
                "contract",
                "site",
                "mgr",
                "PRIVATE",
                "RUB",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"));
    }

    private static final class FakeQuery implements OrderQueryService {
        private OrderDto order;

        @Override
        public PageResult<OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderDto> getOrder(OrderId orderId) {
            return Optional.ofNullable(order);
        }

        @Override
        public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public PageResult<OrderItemRevisionDto> getOrderItemRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderItemRevisionDto> getOrderItemRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderItemRevisionDto> getActiveOrderItemRevision(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemSpecificationDto> getItemSpecification(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getCurrentItemSpecification(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getSpecificationById(
                com.tmp.order.api.SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.OrderForProductionDto> getOrderForProduction(OrderId orderId) {
            return Optional.empty();
        }
    }

    private static final class FakeDocs implements OrderDocumentUiService {
        private boolean saveNewCalled;
        private boolean saveExistingCalled;
        private boolean transferCalled;
        private boolean beginCreateCalled;
        private int transferCalls;
        private OrderId saveNewResult = OrderId.generate();

        @Override
        public UUID beginOrderCreate(String title) {
            beginCreateCalled = true;
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderUpdate(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderApprove(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderActivate(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderCancel(String title, OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public long saveCreateDraft(
                UUID documentId, OrderHeaderDraft draft, long expectedPayloadRevision) {
            return 0L;
        }

        @Override
        public long saveUpdateDraft(
                UUID documentId, OrderId orderId, OrderHeaderDraft draft, long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public OrderId postDocument(UUID documentId) {
            return OrderId.generate();
        }

        @Override
        public OrderId saveNewOrder(OrderHeaderDraft draft) {
            saveNewCalled = true;
            return saveNewResult;
        }

        @Override
        public OrderId saveExistingDraft(OrderId orderId, OrderHeaderDraft draft) {
            saveExistingCalled = true;
            return orderId;
        }

        private Runnable afterTransfer = () -> {};

        @Override
        public OrderId transferToWork(OrderId orderId) {
            transferCalled = true;
            transferCalls++;
            afterTransfer.run();
            return orderId;
        }

        @Override
        public Optional<OrderHeaderDraft> loadCreateDraft(UUID documentId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderHeaderDraft> loadUpdateDraft(UUID documentId) {
            return Optional.empty();
        }
    }
}
