package com.tmp.ui.shell.screen.orderitemeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationRef;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationUiService;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.order.worklist.OrderItemOperationalStatus;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderItemEditorViewModelTest {

    @Test
    void saveNewItemUsesFacade() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId created = OrderItemId.generate();
        docs.saveNewResult = created;
        query.snapshot = snapshot(created, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openCreate(OrderId.generate());
        viewModel.productCodeProperty().set("P-1");
        viewModel.nameProperty().set("Panel");
        viewModel.orderedQuantityProperty().set("2");
        viewModel.save();
        assertTrue(docs.saveNewCalled);
        assertFalse(docs.beginCreateCalled);
        assertEquals(created, viewModel.orderItemIdForTest());
        assertEquals("Позиция сохранена", viewModel.successMessageProperty().get());
    }

    @Test
    void saveExistingItemUsesFacade() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        viewModel.nameProperty().set("Updated");
        viewModel.save();
        assertTrue(docs.saveExistingCalled);
        assertEquals("Updated", docs.lastCommercialDraft.name());
    }

    @Test
    void editableDraftShowsSaveAndCancel() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        assertTrue(viewModel.fieldsEditableProperty().get());
        assertTrue(viewModel.canSaveProperty().get());
        assertTrue(viewModel.canCancelItemProperty().get());
        assertEquals(OrderItemOperationalStatus.EDITING, viewModel.operationalStatusProperty().get());
    }

    @Test
    void readOnlyAfterTransferHidesMutationActions() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        FakeOrderQuery orders = new FakeOrderQuery();
        OrderItemId id = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        query.snapshot =
                OrderItemEditorSnapshot.of(
                        id,
                        orderId,
                        "P-1",
                        "Panel",
                        null,
                        null,
                        OrderItemStatus.ACTIVE,
                        OrderItemEditorSnapshot.RevisionView.of(
                                RevisionNumber.first(), RevisionStatus.ACTIVE, BigDecimal.TEN, 1),
                        null,
                        BigDecimal.TEN);
        orders.status = OrderStatus.ACTIVE;
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(
                        docs, query, auth(allItemPerms()), orders, new FakeCurrentSpec(), null);
        viewModel.openExisting(id);
        assertFalse(viewModel.fieldsEditableProperty().get());
        assertFalse(viewModel.canSaveProperty().get());
        assertFalse(viewModel.canCancelItemProperty().get());
        assertTrue(viewModel.canOpenSpecificationProperty().get());
    }

    @Test
    void openSpecificationUsesCurrentFacade() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        FakeCurrentSpec current = new FakeCurrentSpec();
        OrderItemId id = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        current.ref = CurrentOrderItemSpecificationRef.of(id, revision);
        AtomicReference<OrderItemEditorViewModel.RevisionTarget> opened = new AtomicReference<>();
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(
                        docs, query, auth(allItemPerms()), null, current, null);
        viewModel.setOnOpenSpecification(opened::set);
        viewModel.openExisting(id);
        viewModel.openSpecification();
        assertNotNull(opened.get());
        assertEquals(id, opened.get().orderItemId());
        assertEquals(revision, opened.get().revisionNumber());
    }

    @Test
    void cancelItemStillUsesDocumentApi() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        docs.postResult = id;
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        viewModel.cancelItem();
        assertTrue(docs.cancelCalled);
        assertTrue(docs.postCalled);
    }

    @Test
    void titleUsesProductCode() {
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(new FakeDocs(), query, auth(allItemPerms()));
        viewModel.openExisting(id);
        assertEquals("Позиция P-1", viewModel.titleProperty().get());
    }

    private static Set<PermissionId> allItemPerms() {
        return Set.of(
                PermissionId.of("order.item.view"),
                PermissionId.of("order.item.create"),
                PermissionId.of("order.item.edit"),
                PermissionId.of("order.item.cancel"),
                PermissionId.of("order.item.approve"),
                PermissionId.of("order.revision.create"),
                PermissionId.of("order.revision.edit"),
                PermissionId.of("order.specification.view"));
    }

    private static FakeAuthorization auth(Set<PermissionId> granted) {
        return new FakeAuthorization(granted);
    }

    private static OrderItemEditorSnapshot snapshot(
            OrderItemId id, OrderItemStatus status, boolean withDraft, boolean withActive) {
        OrderItemEditorSnapshot.RevisionView active =
                withActive
                        ? OrderItemEditorSnapshot.RevisionView.of(
                                RevisionNumber.first(),
                                RevisionStatus.ACTIVE,
                                BigDecimal.TEN,
                                1)
                        : null;
        OrderItemEditorSnapshot.RevisionView draft =
                withDraft
                        ? OrderItemEditorSnapshot.RevisionView.of(
                                withActive ? RevisionNumber.of(2) : RevisionNumber.first(),
                                RevisionStatus.DRAFT,
                                BigDecimal.ONE,
                                1)
                        : null;
        BigDecimal quantity =
                draft != null
                        ? draft.orderedQuantity()
                        : active != null ? active.orderedQuantity() : BigDecimal.ONE;
        return OrderItemEditorSnapshot.of(
                id,
                OrderId.generate(),
                "P-1",
                "Panel",
                null,
                "EXT-1",
                status,
                active,
                draft,
                quantity);
    }

    private static final class FakeEditorQuery implements OrderItemEditorQueryService {
        private OrderItemEditorSnapshot snapshot;

        @Override
        public Optional<OrderItemEditorSnapshot> getEditorSnapshot(OrderItemId orderItemId) {
            return Optional.ofNullable(snapshot);
        }
    }

    private static final class FakeCurrentSpec implements CurrentOrderItemSpecificationUiService {
        private CurrentOrderItemSpecificationRef ref;

        @Override
        public Optional<CurrentOrderItemSpecificationRef> resolveCurrent(OrderItemId orderItemId) {
            return Optional.ofNullable(ref);
        }
    }

    private static final class FakeOrderQuery implements OrderQueryService {
        private OrderStatus status = OrderStatus.DRAFT;

        @Override
        public PageResult<OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderDto> getOrder(OrderId orderId) {
            return Optional.of(
                    OrderDto.of(
                            orderId,
                            "O-1",
                            status,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Instant.parse("2026-07-27T10:00:00Z"),
                            Instant.parse("2026-07-27T10:00:00Z")));
        }

        @Override
        public PageResult<com.tmp.order.api.OrderItemDto> getOrderItems(
                OrderId orderId, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemDto> getOrderItem(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public PageResult<com.tmp.order.api.OrderItemRevisionDto> getOrderItemRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemRevisionDto> getOrderItemRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.OrderItemRevisionDto> getActiveOrderItemRevision(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ItemSpecificationDto> getItemSpecification(
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

    private static final class FakeDocs implements OrderItemDocumentUiService {
        private OrderItemId postResult;
        private OrderItemId saveNewResult = OrderItemId.generate();
        private boolean saveNewCalled;
        private boolean saveExistingCalled;
        private boolean beginCreateCalled;
        private boolean postCalled;
        private boolean cancelCalled;
        private OrderItemCommercialDraft lastCommercialDraft;

        @Override
        public UUID beginItemCreate(String title, OrderId orderId) {
            beginCreateCalled = true;
            return UUID.randomUUID();
        }

        @Override
        public UUID beginItemUpdate(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginItemCancel(String title, OrderItemId orderItemId) {
            cancelCalled = true;
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionCreate(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionUpdate(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionApprove(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public long saveItemCreateDraft(
                UUID documentId,
                OrderId orderId,
                Optional<OrderItemId> orderItemId,
                OrderItemCommercialDraft draft,
                String orderedQuantity,
                long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public long saveItemUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                OrderItemCommercialDraft draft,
                long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public long saveRevisionCreateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                Optional<RevisionNumber> copyFromRevisionNumber,
                long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public long saveRevisionUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                String orderedQuantity,
                List<OrderItemSpecificationLineDraft> specificationLines,
                long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public long saveRevisionUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                String orderedQuantity,
                long expectedPayloadRevision) {
            return expectedPayloadRevision + 1;
        }

        @Override
        public OrderItemId postDocument(UUID documentId) {
            postCalled = true;
            return postResult == null ? OrderItemId.generate() : postResult;
        }

        @Override
        public OrderItemId saveNewItem(
                OrderId orderId, OrderItemCommercialDraft draft, String orderedQuantity) {
            saveNewCalled = true;
            lastCommercialDraft = draft;
            return saveNewResult;
        }

        @Override
        public OrderItemId saveExistingItem(
                OrderItemId orderItemId, OrderItemCommercialDraft draft, String orderedQuantity) {
            saveExistingCalled = true;
            lastCommercialDraft = draft;
            return orderItemId;
        }

        @Override
        public Optional<OrderItemCommercialDraft> loadItemCreateDraft(UUID documentId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderItemCommercialDraft> loadItemUpdateDraft(UUID documentId) {
            return Optional.empty();
        }
    }
}
