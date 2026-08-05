package com.tmp.ui.shell.screen.orderspecificationeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrderItemSpecificationEditorViewModelTest {

    @Test
    void draftScreenIsEditableAndSupportsLineOperations() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);

        assertTrue(viewModel.editableProperty().get());
        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("Mat");
        viewModel.editColorProperty().set("Белый");
        viewModel.editLengthMmProperty().set("1500");
        viewModel.editLineQuantityProperty().set("2");
        viewModel.editUnitOfMeasureProperty().set("pcs");
        viewModel.addLine();
        assertEquals(1, viewModel.lines().size());

        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("Mat updated");
        viewModel.editColorProperty().set("");
        viewModel.editLengthMmProperty().set("");
        viewModel.editLineQuantityProperty().set("3");
        viewModel.editUnitOfMeasureProperty().set("pcs");
        viewModel.updateSelectedLine();
        assertEquals("Mat updated", viewModel.lines().get(0).materialName());

        viewModel.editMaterialCodeProperty().set("M2");
        viewModel.editMaterialNameProperty().set("Second");
        viewModel.editColorProperty().set("Серый");
        viewModel.editLengthMmProperty().set("1200");
        viewModel.editLineQuantityProperty().set("1");
        viewModel.editUnitOfMeasureProperty().set("m");
        viewModel.addLine();
        viewModel.selectLine(1);
        viewModel.moveSelectedUp();
        assertEquals("M2", viewModel.lines().get(0).materialCode());

        viewModel.deleteSelectedLine();
        assertEquals(1, viewModel.lines().size());
        viewModel.clearLines();
        assertTrue(viewModel.lines().isEmpty());
    }

    @Test
    void activeSpecificationIsViewOnly() {
        FakeSpecQuery query = new FakeSpecQuery();
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
                                        1, "A", "Active", null, BigDecimal.ONE, BigDecimal.ONE, "pcs")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);

        assertFalse(viewModel.editableProperty().get(), "ACTIVE Specification: view only");
        assertFalse(viewModel.canSaveDraftProperty().get());
        assertFalse(viewModel.canAddLineProperty().get());
    }

    @Test
    void approvedScreenIsReadOnly() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = approvedSnapshot(itemId, revision);
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);

        assertFalse(viewModel.editableProperty().get());
        assertFalse(viewModel.canAddLineProperty().get());
        assertFalse(viewModel.canSaveDraftProperty().get());
        assertFalse(viewModel.canPostProperty().get());
        int before = viewModel.lines().size();
        viewModel.editMaterialCodeProperty().set("X");
        viewModel.editMaterialNameProperty().set("Y");
        viewModel.editColorProperty().set("Черный");
        viewModel.editLengthMmProperty().set("1000");
        viewModel.editLineQuantityProperty().set("1");
        viewModel.editUnitOfMeasureProperty().set("pcs");
        viewModel.addLine();
        assertEquals(before, viewModel.lines().size());
        assertTrue(viewModel.errorMessageProperty().get().contains("просмотр"));
        viewModel.saveDraft();
        assertFalse(docs.saveRevisionUpdateCalled);
    }

    @Test
    void validationErrorsDoNotLoseEnteredData() {
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("");
        viewModel.editColorProperty().set("");
        viewModel.editLengthMmProperty().set("");
        viewModel.editLineQuantityProperty().set("2");
        viewModel.editUnitOfMeasureProperty().set("pcs");
        viewModel.addLine();
        assertTrue(viewModel.lines().isEmpty());
        assertEquals("M1", viewModel.editMaterialCodeProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().length() > 0);
    }

    @Test
    void saveAndPostUseDocumentUiServiceAndReloadSnapshot() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                draftSnapshot(
                        itemId,
                        revision,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1, "M1", "Mat", null, BigDecimal.ONE, BigDecimal.ONE, "pcs")));
        docs.postResult = itemId;
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.saveDraft();
        assertTrue(docs.beginRevisionUpdateCalled);
        assertTrue(docs.saveRevisionUpdateCalled);
        assertEquals(1, docs.lastSavedLines.size());
        assertEquals("M1", docs.lastSavedLines.get(0).materialCode());
        UUID firstDocumentId = viewModel.documentIdForTest();
        viewModel.saveDraft();
        assertEquals(firstDocumentId, viewModel.documentIdForTest());
        assertTrue(viewModel.payloadRevisionForTest() > 0);

        query.snapshot =
                draftSnapshot(
                        itemId,
                        revision,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1, "M1", "Mat", null, BigDecimal.TEN, BigDecimal.ONE, "pcs")));
        viewModel.postDocument();
        assertTrue(docs.postCalled);
        assertNull(viewModel.documentIdForTest());
        assertEquals("10", viewModel.lines().get(0).lengthMm());
        assertEquals("Спецификация обновлена", viewModel.successMessageProperty().get());
    }

    @Test
    void permissionsGateEditing() {
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(
                        new FakeDocs(),
                        query,
                        auth(Set.of(PermissionId.of("order.specification.view"))));
        viewModel.open(itemId, revision);
        assertFalse(viewModel.editableProperty().get());
        assertFalse(viewModel.canSaveDraftProperty().get());
        viewModel.addLine();
        assertEquals(
                com.tmp.ui.shell.order.error.OrderUiErrorMapper.ACCESS_DENIED,
                viewModel.errorMessageProperty().get());
        assertFalse(viewModel.errorMessageProperty().get().contains("утвержд"));
    }

    @Test
    void dirtyStateBlocksPostUntilSavedAgain() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot =
                draftSnapshot(
                        itemId,
                        revision,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1, "M1", "Mat", null, BigDecimal.ONE, BigDecimal.ONE, "pcs")));
        docs.postResult = itemId;
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        assertFalse(viewModel.dirtyForTest());
        viewModel.saveDraft();
        assertTrue(viewModel.canPostProperty().get());

        viewModel.orderedQuantityProperty().set("9");
        assertTrue(viewModel.dirtyForTest());
        assertFalse(viewModel.canPostProperty().get());
        viewModel.postDocument();
        assertFalse(docs.postCalled);
        assertEquals(
                com.tmp.ui.shell.order.error.OrderUiErrorMapper.UNSAVED_CHANGES_BEFORE_POST,
                viewModel.errorMessageProperty().get());
        assertEquals(1, viewModel.lines().size());
        assertEquals("M1", viewModel.lines().get(0).materialCode());

        viewModel.saveDraft();
        assertFalse(viewModel.dirtyForTest());
        assertTrue(viewModel.canPostProperty().get());
        viewModel.postDocument();
        assertTrue(docs.postCalled);
    }

    @Test
    void reloadFailureAfterSuccessfulPostKeepsSuccessAndShowsWarning() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        docs.postResult = itemId;
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.saveDraft();
        query.failNextLoad = true;
        viewModel.postDocument();
        assertTrue(docs.postCalled);
        assertEquals("Спецификация обновлена", viewModel.successMessageProperty().get());
        assertEquals(
                com.tmp.ui.shell.order.error.OrderUiErrorMapper.RELOAD_FAILED_AFTER_POST,
                viewModel.warningMessageProperty().get());
        assertEquals("", viewModel.errorMessageProperty().get());
    }

    @Test
    void backOpensPreviousItem() {
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        AtomicReference<OrderItemId> backTo = new AtomicReference<>();
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.setOnBackToItem(backTo::set);
        viewModel.open(itemId, revision);
        viewModel.backToItem();
        assertEquals(itemId, backTo.get());
    }

    @Test
    void viewModelHasNoRepositoryJdbcOrProcessorFields() {
        for (Field field : OrderItemSpecificationEditorViewModel.class.getDeclaredFields()) {
            String name = field.getType().getName();
            assertFalse(name.contains("JdbcTemplate"));
            assertFalse(name.contains("Repository"));
            assertFalse(name.contains("persistence"));
            assertFalse(name.contains("DocumentProcessor"));
        }
    }

    @Test
    void colorBlankAndLengthBlankAreSavedAsNull() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("Mat");
        viewModel.editColorProperty().set("   ");
        viewModel.editLengthMmProperty().set("");
        viewModel.editLineQuantityProperty().set("2");
        viewModel.editUnitOfMeasureProperty().set("шт");
        viewModel.addLine();
        viewModel.saveDraft();

        assertEquals(null, docs.lastSavedLines.get(0).color());
        assertEquals(null, docs.lastSavedLines.get(0).lengthMm());
    }

    @Test
    void lengthValidationAndLineQuantityValidationUseRussianMessages() {
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("Mat");
        viewModel.editLineQuantityProperty().set("1");
        viewModel.editUnitOfMeasureProperty().set("шт");

        viewModel.editLengthMmProperty().set("abc");
        viewModel.addLine();
        assertEquals("Длина, мм должно быть числом.", viewModel.errorMessageProperty().get());

        viewModel.editLengthMmProperty().set("0");
        viewModel.addLine();
        assertEquals(
                "Длина должна быть больше нуля или оставлена пустой.",
                viewModel.errorMessageProperty().get());

        viewModel.editLengthMmProperty().set("");
        viewModel.editLineQuantityProperty().set("0");
        viewModel.addLine();
        assertEquals("Количество строки должно быть больше нуля.", viewModel.errorMessageProperty().get());
    }

    @Test
    void lineQuantityIsNotMultipliedByProductQuantity() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.orderedQuantityProperty().set("5");
        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("Mat");
        viewModel.editLengthMmProperty().set("100");
        viewModel.editLineQuantityProperty().set("2");
        viewModel.editUnitOfMeasureProperty().set("шт");
        viewModel.addLine();
        viewModel.saveDraft();

        assertEquals(new BigDecimal("2"), docs.lastSavedLines.get(0).lineQuantity());
        assertEquals("5", docs.lastOrderedQuantity);
    }

    @Test
    void orderedQuantityValidationRejectsInvalidValuesWithoutCallingDocumentService() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);

        viewModel.orderedQuantityProperty().set("");
        viewModel.saveDraft();
        assertEquals(
                "Количество изделий обязательно для заполнения.",
                viewModel.errorMessageProperty().get());
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.saveRevisionUpdateCalled);
        assertEquals("", viewModel.orderedQuantityProperty().get());

        viewModel.orderedQuantityProperty().set("abc");
        viewModel.saveDraft();
        assertEquals("Количество изделий должно быть числом.", viewModel.errorMessageProperty().get());
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.saveRevisionUpdateCalled);
        assertEquals("abc", viewModel.orderedQuantityProperty().get());

        viewModel.orderedQuantityProperty().set("1.5");
        viewModel.saveDraft();
        assertEquals(
                "Количество изделий должно быть целым числом больше нуля.",
                viewModel.errorMessageProperty().get());
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.saveRevisionUpdateCalled);

        viewModel.orderedQuantityProperty().set("0");
        viewModel.saveDraft();
        assertEquals(
                "Количество изделий должно быть целым числом больше нуля.",
                viewModel.errorMessageProperty().get());
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.saveRevisionUpdateCalled);

        viewModel.orderedQuantityProperty().set("-1");
        viewModel.saveDraft();
        assertEquals(
                "Количество изделий должно быть целым числом больше нуля.",
                viewModel.errorMessageProperty().get());
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.saveRevisionUpdateCalled);
    }

    @Test
    void importedSpecificationWithScaledProductQuantityCanSaveUnchanged() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        BigDecimal importedQuantity = new BigDecimal("8.000000");
        query.snapshot =
                OrderItemSpecificationEditorSnapshot.of(
                        itemId,
                        revision,
                        RevisionStatus.DRAFT,
                        importedQuantity,
                        false,
                        List.of(
                                OrderItemSpecificationLineView.of(
                                        1,
                                        "107.225белый",
                                        "Штапик",
                                        "Белый",
                                        new BigDecimal("2066.0"),
                                        new BigDecimal("16"),
                                        "шт")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        assertEquals("8", viewModel.orderedQuantityProperty().get());

        viewModel.saveDraft();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(docs.saveRevisionUpdateCalled);
        assertEquals("8", docs.lastOrderedQuantity);
        assertEquals(1, docs.lastSavedLines.size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"8", "8.0", "8.000000"})
    void specificationSaveNormalizesWholeScaledQuantities(String rawQuantity) {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.orderedQuantityProperty().set(rawQuantity);
        viewModel.saveDraft();

        assertEquals("", viewModel.errorMessageProperty().get());
        assertTrue(docs.saveRevisionUpdateCalled);
        assertEquals("8", docs.lastOrderedQuantity);
        assertEquals("8", viewModel.orderedQuantityProperty().get());
    }

    @Test
    void orderedQuantityPositiveIntegerIsPassedUnchangedToContract() {
        FakeDocs docs = new FakeDocs();
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.orderedQuantityProperty().set("2");
        viewModel.saveDraft();

        assertTrue(docs.beginRevisionUpdateCalled);
        assertTrue(docs.saveRevisionUpdateCalled);
        assertEquals("2", docs.lastOrderedQuantity);
    }

    @Test
    void newLineFormDefaultsUnitOfMeasureToPiecesAndResetsAfterAdd() {
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);

        assertEquals("шт", viewModel.editUnitOfMeasureProperty().get());

        viewModel.editMaterialCodeProperty().set("M1");
        viewModel.editMaterialNameProperty().set("Mat");
        viewModel.editLineQuantityProperty().set("1");
        viewModel.editUnitOfMeasureProperty().set("м");
        viewModel.addLine();

        assertEquals("шт", viewModel.editUnitOfMeasureProperty().get());
        assertEquals("м", viewModel.lines().get(0).unitOfMeasure());
    }

    @Test
    void existingLineUnitOfMeasureIsDisplayedWithoutReplacement() {
        FakeSpecQuery query = new FakeSpecQuery();
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
                                        null,
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        "м")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);

        assertEquals("м", viewModel.editUnitOfMeasureProperty().get());
        assertEquals("м", viewModel.lines().get(0).unitOfMeasure());
    }

    @Test
    void publicApiUsesEditLineQuantityAndHasNoConsumptionNormAccessor() {
        for (java.lang.reflect.Method method :
                OrderItemSpecificationEditorViewModel.class.getMethods()) {
            assertFalse(method.getName().equals("editConsumptionNormProperty"));
            assertFalse(method.getName().equals("editQuantityProperty"));
        }
        FakeSpecQuery query = new FakeSpecQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(new FakeDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);
        viewModel.editLineQuantityProperty().set("4");
        assertEquals("4", viewModel.editLineQuantityProperty().get());
    }

    private static Set<PermissionId> allPerms() {
        return Set.of(
                PermissionId.of("order.specification.view"),
                PermissionId.of("order.revision.edit"));
    }

    private static AuthorizationService auth(Set<PermissionId> granted) {
        return new FakeAuthorization(granted);
    }

    private static OrderItemSpecificationEditorSnapshot draftSnapshot(
            OrderItemId itemId,
            RevisionNumber revision,
            List<OrderItemSpecificationLineView> lines) {
        return OrderItemSpecificationEditorSnapshot.of(
                itemId,
                revision,
                RevisionStatus.DRAFT,
                BigDecimal.ONE,
                false,
                lines);
    }

    private static OrderItemSpecificationEditorSnapshot approvedSnapshot(
            OrderItemId itemId, RevisionNumber revision) {
        return OrderItemSpecificationEditorSnapshot.of(
                itemId,
                revision,
                RevisionStatus.ACTIVE,
                BigDecimal.TEN,
                true,
                List.of(
                        OrderItemSpecificationLineView.of(
                                1, "A", "Approved", null, BigDecimal.ONE, BigDecimal.ONE, "pcs")));
    }

    private static final class FakeSpecQuery implements OrderItemSpecificationEditorQueryService {
        private OrderItemSpecificationEditorSnapshot snapshot;
        private boolean failNextLoad;

        @Override
        public Optional<OrderItemSpecificationEditorSnapshot> getSpecificationSnapshot(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            if (failNextLoad) {
                failNextLoad = false;
                throw new RuntimeException("reload boom");
            }
            return Optional.ofNullable(snapshot);
        }
    }

    private static final class FakeDocs implements OrderItemDocumentUiService {
        private OrderItemId postResult;
        private boolean beginRevisionUpdateCalled;
        private boolean saveRevisionUpdateCalled;
        private boolean postCalled;
        private List<OrderItemSpecificationLineDraft> lastSavedLines = List.of();
        private String lastOrderedQuantity;
        private UUID lastDocumentId = UUID.randomUUID();
        private long revision;

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
            beginRevisionUpdateCalled = true;
            lastDocumentId = UUID.randomUUID();
            revision = 0L;
            return lastDocumentId;
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
            revision = expectedPayloadRevision + 1;
            return revision;
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
