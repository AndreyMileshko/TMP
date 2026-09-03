package com.tmp.ui.shell.screen.orderspecificationeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderItemSpecificationEditorViewModelTest {

    @Test
    void openDraftLoadsLinesAndHeaderWithoutRevisionMetadata() {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                draftSnapshot(
                        itemId,
                        revision,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1,
                                        "M1",
                                        "Mat",
                                        "Белый",
                                        new BigDecimal("55.000"),
                                        new BigDecimal("2.500"),
                                        "шт")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        assertTrue(viewModel.titleProperty().get().startsWith("Спецификация позиции"));
        assertEquals("Количество изделий: 1", viewModel.orderedQuantityTextProperty().get());
        assertEquals(1, viewModel.lines().size());
        assertEquals("55", viewModel.lines().get(0).lengthMm());
        assertEquals("2.5", viewModel.lines().get(0).lineQuantity());
        assertTrue(viewModel.editableProperty().get());
    }

    @Test
    void addLinePersistsImmediately() {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.addLine(new SpecificationLineRow("A1", "Name", "", "", "3", "шт"));
        assertTrue(docs.beginRevisionUpdateCalled);
        assertTrue(docs.saveRevisionUpdateCalled);
        assertTrue(docs.postCalled);
        assertEquals(1, docs.lastSavedLines.size());
        assertEquals("A1", docs.lastSavedLines.get(0).materialCode());
    }

    @Test
    void updateAndDeletePersist() {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                draftSnapshot(
                        itemId,
                        revision,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1, "M1", "Mat", null, null, BigDecimal.ONE, "шт")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.selectLine(0);
        viewModel.updateSelectedLine(new SpecificationLineRow("M2", "Mat2", "Red", "10", "2", "шт"));
        assertTrue(docs.postCalled);
        docs.postCalled = false;
        viewModel.selectLine(0);
        viewModel.deleteSelectedLine();
        assertTrue(docs.postCalled);
    }

    @Test
    void moveUpPersistsOrder() {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                draftSnapshot(
                        itemId,
                        revision,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1, "A", "A", null, null, BigDecimal.ONE, "шт"),
                                OrderItemSpecificationLineView.of(
                                        2, "B", "B", null, null, BigDecimal.ONE, "шт")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.selectLine(1);
        viewModel.moveSelectedUp();
        assertTrue(docs.postCalled);
        assertEquals("B", docs.lastSavedLines.get(0).materialCode());
        assertEquals("A", docs.lastSavedLines.get(1).materialCode());
    }

    @Test
    void approvedRevisionIsReadOnly() {
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                OrderItemSpecificationEditorSnapshot.of(
                        itemId,
                        revision,
                        RevisionStatus.ACTIVE,
                        BigDecimal.TEN,
                        true,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1, "A", "Approved", null, null, BigDecimal.ONE, "pcs")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(
                        new TrackingDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);
        assertFalse(viewModel.editableProperty().get());
        assertFalse(viewModel.canAddLineProperty().get());
    }

    @Test
    void readOnlyAddDoesNotPersist() {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                OrderItemSpecificationEditorSnapshot.of(
                        itemId,
                        revision,
                        RevisionStatus.ACTIVE,
                        BigDecimal.TEN,
                        true,
                        List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.addLine(new SpecificationLineRow("X", "Y", "", "", "1", "шт"));
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.postCalled);
    }

    @Test
    void preservesOrderedQuantityOnPersist() {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                OrderItemSpecificationEditorSnapshot.of(
                        itemId,
                        revision,
                        RevisionStatus.DRAFT,
                        new BigDecimal("8"),
                        false,
                        List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.addLine(new SpecificationLineRow("A1", "Name", "", "", "1", "шт"));
        assertEquals("8", docs.lastOrderedQuantity);
    }

    private static Set<PermissionId> allPerms() {
        return Set.of(
                PermissionId.of("order.specification.view"),
                PermissionId.of("order.revision.edit"));
    }

    private static FakeAuthorization auth(Set<PermissionId> granted) {
        return new FakeAuthorization(granted);
    }

    private static OrderItemSpecificationEditorSnapshot draftSnapshot(
            OrderItemId itemId,
            RevisionNumber revision,
            List<OrderItemSpecificationLineView> lines) {
        return OrderItemSpecificationEditorSnapshot.of(
                itemId, revision, RevisionStatus.DRAFT, BigDecimal.ONE, false, lines);
    }

    private static final class EmptyQuery implements OrderItemSpecificationEditorQueryService {
        private OrderItemSpecificationEditorSnapshot snapshot;

        @Override
        public Optional<OrderItemSpecificationEditorSnapshot> getSpecificationSnapshot(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.ofNullable(snapshot);
        }
    }

    private static final class TrackingDocs implements OrderItemDocumentUiService {
        private boolean beginRevisionUpdateCalled;
        private boolean saveRevisionUpdateCalled;
        private boolean postCalled;
        private String lastOrderedQuantity;
        private List<OrderItemSpecificationLineDraft> lastSavedLines = List.of();

        @Override
        public UUID beginItemCreate(String title, com.tmp.order.api.OrderId orderId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginItemUpdate(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginItemCancel(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionCreate(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionUpdate(String title, OrderItemId orderItemId) {
            beginRevisionUpdateCalled = true;
            return UUID.randomUUID();
        }

        @Override
        public UUID beginRevisionApprove(String title, OrderItemId orderItemId) {
            return UUID.randomUUID();
        }

        @Override
        public long saveItemCreateDraft(
                UUID documentId,
                com.tmp.order.api.OrderId orderId,
                Optional<OrderItemId> orderItemId,
                OrderItemCommercialDraft draft,
                String orderedQuantity,
                long expectedPayloadRevision) {
            return expectedPayloadRevision;
        }

        @Override
        public long saveItemUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                OrderItemCommercialDraft draft,
                long expectedPayloadRevision) {
            return expectedPayloadRevision;
        }

        @Override
        public long saveRevisionCreateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                Optional<RevisionNumber> copyFromRevisionNumber,
                long expectedPayloadRevision) {
            return expectedPayloadRevision;
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
            lastOrderedQuantity = orderedQuantity;
            lastSavedLines = new ArrayList<>(specificationLines);
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
            return orderItemIdPlaceholder();
        }

        @Override
        public OrderItemId saveNewItem(
                com.tmp.order.api.OrderId orderId,
                OrderItemCommercialDraft draft,
                String orderedQuantity) {
            return OrderItemId.generate();
        }

        @Override
        public OrderItemId saveExistingItem(
                OrderItemId orderItemId, OrderItemCommercialDraft draft, String orderedQuantity) {
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

        private OrderItemId orderItemIdPlaceholder() {
            return OrderItemId.generate();
        }
    }
}
