package com.tmp.ui.shell.screen.orderspecificationeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderItemSpecificationEditorControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsOrderItemSpecificationEditorFxml() throws Exception {
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(
                        new TrackingDocs(), new EmptyQuery(), new FakeAuthorization());
        Parent root = loadEditor(viewModel);
        assertNotNull(root.lookup("#linesTable"));
    }

    @Test
    void fxmlContainsRequiredColumnsAndFields() throws Exception {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);

        Parent root = loadEditor(viewModel);
        @SuppressWarnings("unchecked")
        TableView<SpecificationLineRow> table =
                (TableView<SpecificationLineRow>) root.lookup("#linesTable");
        assertNotNull(table);
        assertEquals("Артикул", table.getColumns().get(0).getText());
        assertEquals("Наименование", table.getColumns().get(1).getText());
        assertEquals("Цвет", table.getColumns().get(2).getText());
        assertEquals("Длина, мм", table.getColumns().get(3).getText());
        assertEquals("Количество", table.getColumns().get(4).getText());
        assertEquals("Единица измерения", table.getColumns().get(5).getText());

        assertNotNull(root.lookup("#materialCodeField"));
        assertNotNull(root.lookup("#materialNameField"));
        assertNotNull(root.lookup("#colorField"));
        assertNotNull(root.lookup("#lengthMmField"));
        assertNotNull(root.lookup("#lineQuantityField"));
        assertNotNull(root.lookup("#unitOfMeasureField"));
        assertNotNull(root.lookup("#orderedQuantityField"));
    }

    @Test
    void draftFieldsAndButtonsAreEnabledWithPermissions() throws Exception {
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
                new OrderItemSpecificationEditorViewModel(
                        new TrackingDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);

        Parent root = loadEditor(viewModel);
        TextField orderedQuantity = (TextField) root.lookup("#orderedQuantityField");
        TextField materialCode = (TextField) root.lookup("#materialCodeField");
        TextField color = (TextField) root.lookup("#colorField");
        TextField lengthMm = (TextField) root.lookup("#lengthMmField");
        Button addLine = (Button) root.lookup("#addLineButton");
        Button saveDraft = (Button) root.lookup("#saveDraftButton");

        assertFalse(orderedQuantity.isDisabled());
        assertFalse(materialCode.isDisabled());
        assertFalse(color.isDisabled());
        assertFalse(lengthMm.isDisabled());
        assertEquals("", color.getText());
        assertEquals("", lengthMm.getText());
        assertFalse(addLine.isDisabled());
        assertFalse(saveDraft.isDisabled());
    }

    @Test
    void approvedRevisionDisablesEditingControls() throws Exception {
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
                                        1,
                                        "A",
                                        "Approved",
                                        null,
                                        null,
                                        BigDecimal.ONE,
                                        "pcs")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(
                        new TrackingDocs(), query, auth(allPerms()));
        viewModel.open(itemId, revision);

        Parent root = loadEditor(viewModel);
        assertTrue(((TextField) root.lookup("#orderedQuantityField")).isDisabled());
        assertTrue(((TextField) root.lookup("#materialCodeField")).isDisabled());
        assertTrue(((TextField) root.lookup("#lineQuantityField")).isDisabled());
        assertTrue(((Button) root.lookup("#addLineButton")).isDisabled());
        assertTrue(((Button) root.lookup("#saveDraftButton")).isDisabled());
        assertTrue(((Button) root.lookup("#updateLineButton")).isDisabled());
        assertTrue(((Button) root.lookup("#deleteLineButton")).isDisabled());
    }

    @Test
    void invalidOrderedQuantityShowsRussianErrorAndDoesNotCallDocumentService() throws Exception {
        TrackingDocs docs = new TrackingDocs();
        EmptyQuery query = new EmptyQuery();
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        query.snapshot = draftSnapshot(itemId, revision, List.of());
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);

        Parent root = loadEditor(viewModel);
        TextField orderedQuantity = (TextField) root.lookup("#orderedQuantityField");
        Button saveDraft = (Button) root.lookup("#saveDraftButton");
        Label errorLabel = (Label) root.lookup("#errorLabel");

        runFx(
                () -> {
                    orderedQuantity.setText("abc");
                    saveDraft.fire();
                });

        assertEquals("Количество изделий должно быть числом.", errorLabel.getText());
        assertFalse(docs.beginRevisionUpdateCalled);
        assertFalse(docs.saveRevisionUpdateCalled);
        assertEquals("abc", orderedQuantity.getText());
    }

    @Test
    void validSaveUsesOrderItemDocumentUiService() throws Exception {
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
                                        1, "M1", "Mat", "Белый", BigDecimal.TEN, BigDecimal.TWO, "шт")));
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(docs, query, auth(allPerms()));
        viewModel.open(itemId, revision);

        Parent root = loadEditor(viewModel);
        TextField orderedQuantity = (TextField) root.lookup("#orderedQuantityField");
        Button saveDraft = (Button) root.lookup("#saveDraftButton");

        runFx(
                () -> {
                    orderedQuantity.setText("3");
                    saveDraft.fire();
                });

        assertTrue(docs.beginRevisionUpdateCalled);
        assertTrue(docs.saveRevisionUpdateCalled);
        assertEquals("3", docs.lastOrderedQuantity);
        assertEquals(1, docs.lastSavedLines.size());
        assertEquals(new BigDecimal("2"), docs.lastSavedLines.get(0).lineQuantity());
    }

    private static Parent loadEditor(OrderItemSpecificationEditorViewModel viewModel)
            throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(
                new ScreenRegistration(
                        UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_FXML,
                        () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        Parent root =
                                navigation.load(
                                        UiShellScreens.ORDER_ITEM_SPECIFICATION_EDITOR_SCREEN_ID);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(root));
                        rootRef.set(root);
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Specification editor FX load failed", error.get());
        }
        return rootRef.get();
    }

    private static void runFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX action failed", error.get());
        }
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
        private String lastOrderedQuantity;
        private List<OrderItemSpecificationLineDraft> lastSavedLines = List.of();

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
            return OrderItemId.generate();
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
