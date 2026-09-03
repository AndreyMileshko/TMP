package com.tmp.ui.shell.screen.orderspecificationeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
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
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderItemSpecificationEditorControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsModernSpecificationEditorWithoutLegacyForm() throws Exception {
        OrderItemSpecificationEditorViewModel viewModel =
                new OrderItemSpecificationEditorViewModel(
                        new TrackingDocs(), new EmptyQuery(), new FakeAuthorization());
        Parent root = loadEditor(viewModel);
        assertNotNull(root.lookup("#linesTable"));
        assertNotNull(root.lookup("#addMaterialButton"));
        assertNotNull(root.lookup("#orderedQuantityLabel"));
        assertNull(root.lookup("#revisionNumberLabel"));
        assertNull(root.lookup("#revisionStatusLabel"));
        assertNull(root.lookup("#materialCodeField"));
        assertNull(root.lookup("#saveDraftButton"));
        assertNull(root.lookup("#postButton"));
        assertNull(root.lookup("#addLineButton"));
        assertNull(root.lookup("#orderedQuantityField"));
    }

    @Test
    void fxmlContainsRequiredColumnsAndHeader() throws Exception {
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
        Label qty = (Label) root.lookup("#orderedQuantityLabel");
        assertEquals("Количество изделий: 1", qty.getText());
        assertTrue(((Button) root.lookup("#addMaterialButton")).isVisible());
    }

    @Test
    void readOnlyHidesAddButton() throws Exception {
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

        Parent root = loadEditor(viewModel);
        assertFalse(((Button) root.lookup("#addMaterialButton")).isVisible());
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
            return expectedPayloadRevision + 1;
        }

        @Override
        public long saveRevisionUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                String orderedQuantity,
                long expectedPayloadRevision) {
            return expectedPayloadRevision;
        }

        @Override
        public OrderItemId postDocument(UUID documentId) {
            return OrderItemId.generate();
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
    }
}
