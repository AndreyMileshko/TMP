package com.tmp.ui.shell.screen.orderitemeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderItemEditorControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsOrderItemEditorFxml() throws Exception {
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(
                        new EmptyDocs(), new EmptyQuery(), new FakeAuthorization());
        viewModel.openCreate(OrderId.generate());
        Parent root = loadEditor(viewModel);
        assertNotNull(root.lookup("#productCodeField"));
        assertNotNull(root.lookup("#externalPositionNumberField"));
    }

    @Test
    void externalPositionNumberFieldIsBoundAndShowsSnapshotValueInDraft() throws Exception {
        EmptyQuery query = new EmptyQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.DRAFT, true, false, "EXT-77");
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(new EmptyDocs(), query, auth(allItemPerms()));
        viewModel.openExisting(id);

        Parent root = loadEditor(viewModel);
        TextField field = (TextField) root.lookup("#externalPositionNumberField");
        assertNotNull(field);
        assertEquals("EXT-77", field.getText());
        assertFalse(field.isDisabled());

        runFx(
                () -> {
                    field.setText("EXT-88");
                    assertEquals("EXT-88", viewModel.externalPositionNumberProperty().get());
                    viewModel.externalPositionNumberProperty().set("EXT-99");
                    assertEquals("EXT-99", field.getText());
                });
    }

    @Test
    void nullExternalPositionNumberShowsEmptyStringAndImmutableDisablesField() throws Exception {
        EmptyQuery query = new EmptyQuery();
        OrderItemId id = OrderItemId.generate();
        query.snapshot = snapshot(id, OrderItemStatus.ACTIVE, false, true, null);
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(new EmptyDocs(), query, auth(allItemPerms()));
        viewModel.openExisting(id);

        Parent root = loadEditor(viewModel);
        TextField field = (TextField) root.lookup("#externalPositionNumberField");
        assertNotNull(field);
        assertEquals("", field.getText());
        assertFalse("null".equalsIgnoreCase(field.getText()));
        assertTrue(field.isDisabled());
    }

    private static Parent loadEditor(OrderItemEditorViewModel viewModel) throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(
                new ScreenRegistration(
                        UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID,
                        UiShellScreens.ORDER_ITEM_EDITOR_FXML,
                        () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        Parent root = navigation.load(UiShellScreens.ORDER_ITEM_EDITOR_SCREEN_ID);
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
            throw new AssertionError("Order item editor FX load failed", error.get());
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
            OrderItemId id,
            OrderItemStatus status,
            boolean withDraft,
            boolean withActive,
            String externalPositionNumber) {
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
                externalPositionNumber,
                status,
                active,
                draft,
                quantity);
    }

    private static final class EmptyQuery implements OrderItemEditorQueryService {
        private OrderItemEditorSnapshot snapshot;

        @Override
        public Optional<OrderItemEditorSnapshot> getEditorSnapshot(OrderItemId orderItemId) {
            return Optional.ofNullable(snapshot);
        }
    }

    private static final class EmptyDocs implements OrderItemDocumentUiService {
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
                long expectedPayloadRevision) {
            return expectedPayloadRevision;
        }

        @Override
        public long saveRevisionUpdateDraft(
                UUID documentId,
                OrderItemId orderItemId,
                RevisionNumber revisionNumber,
                String orderedQuantity,
                java.util.List<com.tmp.order.api.ui.OrderItemSpecificationLineDraft>
                        specificationLines,
                long expectedPayloadRevision) {
            return expectedPayloadRevision;
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
