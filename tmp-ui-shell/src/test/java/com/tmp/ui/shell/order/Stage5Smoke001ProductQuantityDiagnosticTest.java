package com.tmp.ui.shell.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.tmp.ui.shell.screen.orderitemeditor.OrderItemEditorViewModel;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Diagnostic coverage for STAGE5-057-SMOKE-001: pin the Russian whole-quantity message to UI
 * validation and prove user-facing Save for imported scaled quantities does not fail.
 */
class Stage5Smoke001ProductQuantityDiagnosticTest {

    private static final String RUSSIAN_WHOLE_MESSAGE =
            "Количество изделий должно быть целым числом больше нуля.";

    @Test
    void russianWholeQuantityMessageIsProducedByUiValidatorOnly() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProductQuantityUiValidation.requireValidProductQuantity("8.5"));
        assertEquals(RUSSIAN_WHOLE_MESSAGE, ex.getMessage());

        // Commercial DTO has no quantity and must not emit that message.
        OrderItemCommercialDraft draft =
                OrderItemCommercialDraft.of("P-1", "Panel", "comment", "1");
        assertEquals("comment", draft.comments());
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "8.0", "8.000000"})
    void importedDraftSaveSucceedsForScaledWholeQuantitiesOnSamePath(String rawQuantity) {
        RecordingDocs docs = new RecordingDocs();
        FakeEditorQuery query = new FakeEditorQuery(new BigDecimal("8.000000"));
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));

        viewModel.openExisting(query.itemId);
        assertEquals("8", viewModel.orderedQuantityProperty().get());

        viewModel.orderedQuantityProperty().set(rawQuantity);
        viewModel.commentsProperty().set("smoke-diagnostic");
        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertFalse(viewModel.errorMessageProperty().get().contains("Количество изделий"));
        assertTrue(docs.saveExistingCalled);
        assertFalse(docs.saveNewCalled);
        assertEquals("smoke-diagnostic", docs.lastCommercialDraft.comments());
        assertEquals("8", docs.lastOrderedQuantity);
        assertEquals("8", viewModel.orderedQuantityProperty().get());
    }

    @Test
    void saveWithoutChangingImportedScaledQuantitySucceeds() {
        RecordingDocs docs = new RecordingDocs();
        FakeEditorQuery query = new FakeEditorQuery(new BigDecimal("8.000000"));
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(docs, query, auth(allItemPerms()));

        viewModel.openExisting(query.itemId);
        viewModel.save();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(docs.saveExistingCalled);
        assertTrue(viewModel.successMessageProperty().get().contains("Позиция сохранена"));
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

    private static final class FakeEditorQuery implements OrderItemEditorQueryService {
        private final OrderItemId itemId = OrderItemId.generate();
        private final OrderItemEditorSnapshot snapshot;

        private FakeEditorQuery(BigDecimal importedQuantity) {
            snapshot =
                    OrderItemEditorSnapshot.of(
                            itemId,
                            OrderId.generate(),
                            null,
                            null,
                            "old-comment",
                            "1",
                            OrderItemStatus.DRAFT,
                            null,
                            OrderItemEditorSnapshot.RevisionView.of(
                                    RevisionNumber.first(),
                                    RevisionStatus.DRAFT,
                                    importedQuantity,
                                    2),
                            importedQuantity);
        }

        @Override
        public Optional<OrderItemEditorSnapshot> getEditorSnapshot(OrderItemId orderItemId) {
            return Optional.of(snapshot);
        }
    }

    private static final class RecordingDocs implements OrderItemDocumentUiService {
        private boolean saveNewCalled;
        private boolean saveExistingCalled;
        private OrderItemCommercialDraft lastCommercialDraft;
        private String lastOrderedQuantity;

        @Override
        public UUID beginItemCreate(String title, OrderId orderId) {
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
        public OrderItemId postDocument(UUID documentId) {
            return OrderItemId.generate();
        }

        @Override
        public OrderItemId saveNewItem(
                OrderId orderId, OrderItemCommercialDraft draft, String orderedQuantity) {
            saveNewCalled = true;
            lastCommercialDraft = draft;
            lastOrderedQuantity = orderedQuantity;
            return OrderItemId.generate();
        }

        @Override
        public OrderItemId saveExistingItem(
                OrderItemId orderItemId, OrderItemCommercialDraft draft, String orderedQuantity) {
            saveExistingCalled = true;
            lastCommercialDraft = draft;
            lastOrderedQuantity = orderedQuantity;
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
