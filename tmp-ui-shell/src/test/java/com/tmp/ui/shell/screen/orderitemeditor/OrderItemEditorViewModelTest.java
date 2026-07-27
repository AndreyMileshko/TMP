package com.tmp.ui.shell.screen.orderitemeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderItemEditorViewModelTest {

    @Test
    void createSaveAndPostUsesDocumentUiServiceOnly() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId created = OrderItemId.generate();
        docs.postResult = created;
        query.snapshot = snapshot(created, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openCreate(OrderId.generate());
        viewModel.productCodeProperty().set("P-1");
        viewModel.nameProperty().set("Panel");
        viewModel.orderedQuantityProperty().set("2");
        viewModel.saveCommercialDraft();
        assertEquals("ORDER_ITEM_CREATE", docs.lastBeginType);
        assertTrue(docs.saveCreateCalled);
        viewModel.postCommercialDocument();
        assertTrue(docs.postCalled);
        assertEquals(created, viewModel.orderItemIdForTest());
        assertEquals("Документ позиции проведён", viewModel.successMessageProperty().get());
    }

    @Test
    void successMessagePreservedAfterReload() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        docs.postResult = id;
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        viewModel.saveCommercialDraft();
        viewModel.postCommercialDocument();
        assertEquals("Документ позиции проведён", viewModel.successMessageProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    void draftAndActiveRevisionsDisplayedSeparately() {
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, true, true);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(new FakeDocs(), query, auth(allItemPerms()));
        viewModel.openExisting(id);
        assertTrue(viewModel.activeRevisionTextProperty().get().startsWith("1"));
        assertTrue(viewModel.draftRevisionTextProperty().get().startsWith("2"));
        assertEquals("1", viewModel.draftSpecLineCountTextProperty().get());
    }

    @Test
    void cancelAllowedOnlyForDraftItem() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId draftId = OrderItemId.generate();
        query.snapshot = snapshot(draftId, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(draftId);
        assertTrue(viewModel.canCancelItemProperty().get());

        OrderItemId activeId = OrderItemId.generate();
        query.snapshot = snapshot(activeId, OrderItemStatus.ACTIVE, false, true);
        viewModel.openExisting(activeId);
        assertFalse(viewModel.canCancelItemProperty().get());
        viewModel.cancelItem();
        assertFalse(docs.cancelCalled);
        assertTrue(viewModel.errorMessageProperty().get().length() > 0);
    }

    @Test
    void secondDraftRevisionIsBlocked() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, true, true);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        assertFalse(viewModel.canCreateRevisionProperty().get());
        viewModel.createNextRevision();
        assertFalse(docs.revisionCreateCalled);
        assertTrue(viewModel.errorMessageProperty().get().contains("чернов"));
    }

    @Test
    void createNextRevisionPostsRevisionCreateDocument() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        docs.postResult = id;
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, false, true);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        assertTrue(viewModel.canCreateRevisionProperty().get());
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, true, true);
        viewModel.createNextRevision();
        assertTrue(docs.revisionCreateCalled);
        assertTrue(docs.saveRevisionCreateCalled);
        assertTrue(docs.postCalled);
        assertTrue(viewModel.successMessageProperty().get().contains("создана"));
    }

    @Test
    void revisionQuantityUpdateUsesRevisionUpdateDocument() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        docs.postResult = id;
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        viewModel.orderedQuantityProperty().set("9");
        viewModel.saveRevisionQuantityDraft();
        assertTrue(docs.saveRevisionUpdateCalled);
        viewModel.postRevisionUpdate();
        assertTrue(docs.postCalled);
        assertEquals("Количество черновой редакции обновлено", viewModel.successMessageProperty().get());
    }

    @Test
    void approveRevisionReloadsActivePointer() {
        FakeDocs docs = new FakeDocs();
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        docs.postResult = id;
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));
        viewModel.openExisting(id);
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, false, true);
        viewModel.approveDraftRevision();
        assertTrue(docs.approveCalled);
        assertTrue(viewModel.activeRevisionTextProperty().get().startsWith("1"));
        assertEquals("—", viewModel.draftRevisionTextProperty().get());
        assertEquals("Редакция утверждена", viewModel.successMessageProperty().get());
    }

    @Test
    void permissionsHideUnavailableActions() {
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(
                        new FakeDocs(),
                        query,
                        auth(Set.of(PermissionId.of("order.item.view"))));
        viewModel.openExisting(id);
        assertFalse(viewModel.canSaveCommercialDraftProperty().get());
        assertFalse(viewModel.canCancelItemProperty().get());
        assertFalse(viewModel.canApproveRevisionProperty().get());
        assertFalse(viewModel.canOpenDraftSpecificationProperty().get());
    }

    @Test
    void openSpecificationActionsRequireViewPermissionAndRevision() {
        FakeEditorQuery query = new FakeEditorQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, true, true);
        AtomicReference<OrderItemEditorViewModel.RevisionTarget> opened = new AtomicReference<>();
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(new FakeDocs(), query, auth(allItemPerms()));
        viewModel.setOnOpenSpecification(opened::set);
        viewModel.openExisting(id);
        assertTrue(viewModel.canOpenActiveSpecificationProperty().get());
        assertTrue(viewModel.canOpenDraftSpecificationProperty().get());
        viewModel.openDraftSpecification();
        assertEquals(id, opened.get().orderItemId());
        assertEquals(2, opened.get().revisionNumber().value());
        viewModel.openActiveSpecification();
        assertEquals(1, opened.get().revisionNumber().value());
    }

    @Test
    void viewModelHasNoRepositoryOrJdbcFields() {
        for (Field field : OrderItemEditorViewModel.class.getDeclaredFields()) {
            String name = field.getType().getName();
            assertFalse(name.contains("JdbcTemplate"));
            assertFalse(name.contains("Repository"));
            assertFalse(name.contains("persistence"));
            assertFalse(name.contains("DocumentProcessor"));
        }
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

    private static AuthorizationService auth(Set<PermissionId> granted) {
        return new FakeAuthorization(granted);
    }

    private static OrderItemEditorSnapshot snapshot(
            OrderItemId id, OrderItemStatus status, boolean withDraft, boolean withActive) {
        OrderItemEditorSnapshot.RevisionView active =
                withActive
                        ? OrderItemEditorSnapshot.RevisionView.of(
                                RevisionNumber.first(),
                                RevisionStatus.APPROVED,
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

    private static final class FakeDocs implements OrderItemDocumentUiService {
        private OrderItemId postResult;
        private String lastBeginType;
        private boolean saveCreateCalled;
        private boolean saveRevisionCreateCalled;
        private boolean saveRevisionUpdateCalled;
        private boolean postCalled;
        private boolean cancelCalled;
        private boolean revisionCreateCalled;
        private boolean approveCalled;
        private UUID lastDocumentId = UUID.randomUUID();

        @Override
        public UUID beginItemCreate(String title, OrderId orderId) {
            lastBeginType = "ORDER_ITEM_CREATE";
            lastDocumentId = UUID.randomUUID();
            return lastDocumentId;
        }

        @Override
        public UUID beginItemUpdate(String title, OrderItemId orderItemId) {
            lastBeginType = "ORDER_ITEM_UPDATE";
            lastDocumentId = UUID.randomUUID();
            return lastDocumentId;
        }

        @Override
        public UUID beginItemCancel(String title, OrderItemId orderItemId) {
            cancelCalled = true;
            lastBeginType = "ORDER_ITEM_CANCEL";
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionCreate(String title, OrderItemId orderItemId) {
            revisionCreateCalled = true;
            lastBeginType = "ORDER_ITEM_REVISION_CREATE";
            lastDocumentId = UUID.randomUUID();
            return lastDocumentId;
        }

        @Override
        public UUID beginRevisionUpdate(String title, OrderItemId orderItemId) {
            lastBeginType = "ORDER_ITEM_REVISION_UPDATE";
            lastDocumentId = UUID.randomUUID();
            return lastDocumentId;
        }

        @Override
        public UUID beginRevisionApprove(String title, OrderItemId orderItemId) {
            approveCalled = true;
            lastBeginType = "ORDER_ITEM_REVISION_APPROVE";
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
            saveCreateCalled = true;
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
            saveRevisionCreateCalled = true;
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
            saveRevisionUpdateCalled = true;
            return expectedPayloadRevision + 1;
        }

        @Override
        public long saveRevisionUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                String orderedQuantity,
                long expectedPayloadRevision) {
            return saveRevisionUpdateDraft(
                    documentId,
                    orderItemId,
                    revisionNumber,
                    orderedQuantity,
                    List.of(),
                    expectedPayloadRevision);
        }

        @Override
        public OrderItemId postDocument(UUID documentId) {
            postCalled = true;
            return postResult;
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
