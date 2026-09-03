package com.tmp.ui.shell.screen.orderitemeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.Instant;
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
    void loadsOrderItemEditorFxmlWithoutTechnicalControls() throws Exception {
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(
                        new EmptyDocs(), new EmptyQuery(), new FakeAuthorization());
        viewModel.openCreate(OrderId.generate());
        Parent root = loadEditor(viewModel);
        assertNotNull(root.lookup("#productCodeField"));
        assertNotNull(root.lookup("#saveButton"));
        assertNotNull(root.lookup("#openSpecificationButton"));
        assertNull(root.lookup("#saveCommercialButton"));
        assertNull(root.lookup("#postCommercialButton"));
        assertNull(root.lookup("#createRevisionButton"));
        assertNull(root.lookup("#openActiveSpecificationButton"));
        assertNull(root.lookup("#openDraftSpecificationButton"));
        assertNull(root.lookup("#activeRevisionLabel"));
        assertNull(root.lookup("#copyFromRevisionField"));
    }

    @Test
    void draftModeShowsEditableFields() throws Exception {
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
        assertTrue(field.isVisible());
        assertTrue(((Button) root.lookup("#saveButton")).isVisible());
        assertTrue(((Button) root.lookup("#cancelItemButton")).isVisible());
    }

    @Test
    void readOnlyAfterTransferHidesSaveAndShowsLabels() throws Exception {
        EmptyQuery query = new EmptyQuery();
        FakeOrderQuery orders = new FakeOrderQuery();
        OrderItemId id = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        orders.status = OrderStatus.ACTIVE;
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
        OrderItemEditorViewModel viewModel =
                new OrderItemEditorViewModel(
                        new EmptyDocs(),
                        query,
                        auth(allItemPerms()),
                        orders,
                        orderItemId -> Optional.empty(),
                        null);
        viewModel.openExisting(id);

        Parent root = loadEditor(viewModel);
        assertFalse(((TextField) root.lookup("#productCodeField")).isVisible());
        assertTrue(((Label) root.lookup("#productCodeValueLabel")).isVisible());
        assertFalse(((Button) root.lookup("#saveButton")).isVisible());
        assertFalse(((Button) root.lookup("#cancelItemButton")).isVisible());
        assertTrue(((Button) root.lookup("#openSpecificationButton")).isVisible());
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
        public OrderItemId saveNewItem(
                OrderId orderId, OrderItemCommercialDraft draft, String orderedQuantity) {
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
