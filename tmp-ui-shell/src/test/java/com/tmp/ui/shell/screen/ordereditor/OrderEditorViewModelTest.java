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
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderEditorViewModelTest {

    @Test
    void openCreateEnablesDraftActionsWithCreatePermission() {
        FakeDocs docs = new FakeDocs();
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(new FakeQuery(), docs, auth(allOrderPerms()));
        viewModel.openCreate();
        assertEquals(OrderEditorViewModel.Mode.CREATE, viewModel.modeProperty().get());
        assertTrue(viewModel.canSaveDraftProperty().get());
        assertFalse(viewModel.canPostProperty().get());
        assertFalse(viewModel.canApproveProperty().get());
    }

    @Test
    void openExistingDraftAllowsEditApproveCancel() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, new FakeDocs(), auth(allOrderPerms()));
        viewModel.openExisting(id);
        assertTrue(viewModel.fieldsEditableProperty().get());
        assertTrue(viewModel.canApproveProperty().get());
        assertTrue(viewModel.canCancelProperty().get());
        assertFalse(viewModel.orderNumberEditableProperty().get());
    }

    @Test
    void approvedAndCancelledAreReadOnly() {
        OrderId approvedId = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(approvedId, OrderStatus.APPROVED);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, new FakeDocs(), auth(allOrderPerms()));
        viewModel.openExisting(approvedId);
        assertFalse(viewModel.fieldsEditableProperty().get());
        assertFalse(viewModel.canSaveDraftProperty().get());
        assertFalse(viewModel.canApproveProperty().get());
        assertFalse(viewModel.canCancelProperty().get());

        OrderId cancelledId = OrderId.generate();
        query.order = order(cancelledId, OrderStatus.CANCELLED);
        viewModel.openExisting(cancelledId);
        assertFalse(viewModel.fieldsEditableProperty().get());
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void saveAndPostCreateUsesDocumentUiServiceOnly() {
        FakeDocs docs = new FakeDocs();
        FakeQuery query = new FakeQuery();
        OrderId created = OrderId.generate();
        docs.postResult = created;
        query.order = order(created, OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, docs, auth(allOrderPerms()));
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("A-100");
        viewModel.customerNameProperty().set("Acme");
        viewModel.saveDraft();
        assertEquals("ORDER_CREATE", docs.lastBeginType);
        assertTrue(docs.saveCreateCalled);
        assertTrue(viewModel.canPostProperty().get());
        viewModel.postCurrentDocument();
        assertTrue(docs.postCalled);
        assertEquals(created, viewModel.orderIdForTest());
        assertEquals(OrderEditorViewModel.Mode.VIEW_EXISTING, viewModel.modeProperty().get());
    }

    @Test
    void successMessagePreservedAfterPostReload() {
        FakeDocs docs = new FakeDocs();
        FakeQuery query = new FakeQuery();
        OrderId created = OrderId.generate();
        docs.postResult = created;
        query.order = order(created, OrderStatus.DRAFT);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, docs, auth(allOrderPerms()));
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("A-100");
        viewModel.customerNameProperty().set("Acme");
        viewModel.saveDraft();
        viewModel.postCurrentDocument();
        assertEquals("Документ проведён", viewModel.successMessageProperty().get());
        assertEquals(OrderEditorViewModel.Mode.VIEW_EXISTING, viewModel.modeProperty().get());
    }

    @Test
    void errorKeepsUserInputAndDoesNotShowSuccess() {
        FakeDocs docs = new FakeDocs();
        docs.failSave = true;
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(new FakeQuery(), docs, auth(allOrderPerms()));
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("KEEP-ME");
        viewModel.customerNameProperty().set("Kept Customer");
        viewModel.saveDraft();
        assertEquals("KEEP-ME", viewModel.orderNumberProperty().get());
        assertEquals("Kept Customer", viewModel.customerNameProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().length() > 0);
        assertEquals("", viewModel.successMessageProperty().get());
    }

    @Test
    void failedPostKeepsDocumentIdAndShowsMappedAlreadyPosted() {
        FakeDocs docs = new FakeDocs();
        FakeQuery query = new FakeQuery();
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(query, docs, auth(allOrderPerms()));
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("A-100");
        viewModel.customerNameProperty().set("Acme");
        viewModel.saveDraft();
        UUID documentId = viewModel.documentIdForTest();
        docs.failPost =
                new IllegalStateException(
                        "Operation requires DRAFT status: 22222222-2222-2222-2222-222222222222");
        viewModel.postCurrentDocument();
        assertEquals(documentId, viewModel.documentIdForTest());
        assertEquals("A-100", viewModel.orderNumberProperty().get());
        assertEquals(
                com.tmp.ui.shell.order.error.OrderUiErrorMapper.ALREADY_POSTED,
                viewModel.errorMessageProperty().get());
        assertEquals("", viewModel.successMessageProperty().get());
        assertFalse(viewModel.errorMessageProperty().get().contains("22222222"));
    }

    @Test
    void optimisticLockOnSaveShowsDedicatedMessage() {
        FakeDocs docs = new FakeDocs();
        docs.failSaveException = new PayloadOptimisticLockExceptionForTest();
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(new FakeQuery(), docs, auth(allOrderPerms()));
        viewModel.openCreate();
        viewModel.orderNumberProperty().set("A-100");
        viewModel.customerNameProperty().set("Acme");
        viewModel.saveDraft();
        assertEquals(
                com.tmp.ui.shell.order.error.OrderUiErrorMapper.OPTIMISTIC_LOCK,
                viewModel.errorMessageProperty().get());
        assertEquals("A-100", viewModel.orderNumberProperty().get());
    }

    @Test
    void permissionsHideUnavailableActions() {
        OrderId id = OrderId.generate();
        FakeQuery query = new FakeQuery();
        query.order = order(id, OrderStatus.DRAFT);
        OrderEditorViewModel viewModel = new OrderEditorViewModel(
                query, new FakeDocs(), auth(Set.of(PermissionId.of("order.order.view"))));
        viewModel.openExisting(id);
        assertFalse(viewModel.canSaveDraftProperty().get());
        assertFalse(viewModel.canApproveProperty().get());
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
    void backToListCallbackRuns() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        OrderEditorViewModel viewModel =
                new OrderEditorViewModel(new FakeQuery(), new FakeDocs(), auth(allOrderPerms()));
        viewModel.setOnBackToList(() -> called.set(true));
        viewModel.backToList();
        assertTrue(called.get());
    }

    private static Set<PermissionId> allOrderPerms() {
        return Set.of(
                PermissionId.of("order.order.view"),
                PermissionId.of("order.order.create"),
                PermissionId.of("order.order.edit"),
                PermissionId.of("order.order.approve"),
                PermissionId.of("order.order.cancel"));
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
    }

    private static final class FakeDocs implements OrderDocumentUiService {
        private String lastBeginType;
        private boolean saveCreateCalled;
        private boolean postCalled;
        private boolean failSave;
        private RuntimeException failSaveException;
        private RuntimeException failPost;
        private OrderId postResult = OrderId.generate();
        private UUID lastDocumentId = UUID.randomUUID();

        @Override
        public UUID beginOrderCreate(String title) {
            lastBeginType = "ORDER_CREATE";
            lastDocumentId = UUID.randomUUID();
            return lastDocumentId;
        }

        @Override
        public UUID beginOrderUpdate(String title, OrderId orderId) {
            lastBeginType = "ORDER_UPDATE";
            lastDocumentId = UUID.randomUUID();
            return lastDocumentId;
        }

        @Override
        public UUID beginOrderApprove(String title, OrderId orderId) {
            lastBeginType = "ORDER_APPROVE";
            return UUID.randomUUID();
        }

        @Override
        public UUID beginOrderCancel(String title, OrderId orderId) {
            lastBeginType = "ORDER_CANCEL";
            return UUID.randomUUID();
        }

        @Override
        public long saveCreateDraft(
                UUID documentId, OrderHeaderDraft draft, long expectedPayloadRevision) {
            if (failSaveException != null) {
                throw failSaveException;
            }
            if (failSave) {
                throw new IllegalStateException("payload create failed");
            }
            saveCreateCalled = true;
            return expectedPayloadRevision == 0 ? 0 : expectedPayloadRevision + 1;
        }

        @Override
        public long saveUpdateDraft(
                UUID documentId,
                OrderId orderId,
                OrderHeaderDraft draft,
                long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public OrderId postDocument(UUID documentId) {
            if (failPost != null) {
                throw failPost;
            }
            postCalled = true;
            return postResult;
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

    private static final class PayloadOptimisticLockExceptionForTest extends RuntimeException {
        private PayloadOptimisticLockExceptionForTest() {
            super("Payload revision conflict for document");
        }
    }
}
