package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderOperationalStatusIndicatorTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void eachOperationalStatusMapsToDedicatedIndicatorStyleClass() {
        assertEquals("tmp-status-dot-neutral", OrderOperationalStatus.EDITING.indicatorStyleClass());
        assertEquals(
                "tmp-status-dot-warning", OrderOperationalStatus.AWAITING_PRODUCTION.indicatorStyleClass());
        assertEquals("tmp-status-dot-info", OrderOperationalStatus.IN_PRODUCTION.indicatorStyleClass());
        assertEquals("tmp-status-dot-success", OrderOperationalStatus.COMPLETED.indicatorStyleClass());
        assertEquals(
                "tmp-status-dot-warning-strong",
                OrderOperationalStatus.PARTIALLY_COMPLETED.indicatorStyleClass());
        assertEquals("tmp-status-dot-danger", OrderOperationalStatus.CANCELLED.indicatorStyleClass());
        assertEquals(
                "tmp-status-dot-unavailable",
                OrderOperationalStatus.STATUS_UNAVAILABLE.indicatorStyleClass());
        assertEquals(EnumSet.allOf(OrderOperationalStatus.class).size(), 7);
    }

    @Test
    void statusCellUsesDotPlusReadableCaptionWithoutRawStyleColors() throws Exception {
        Path controller =
                Path.of(
                        "src/main/java/com/tmp/ui/shell/screen/orderlist/OrderListController.java");
        String source = Files.readString(controller);
        assertFalse(source.contains("setStyle("));
        assertTrue(source.contains("OperationalStatusIndicator.create"));

        JavaFxTestSupport.runOnFxThread(() -> {
            for (OrderOperationalStatus status : OrderOperationalStatus.values()) {
                HBox box = OperationalStatusIndicator.create(status);
                assertFalse(status.caption().isBlank());
                assertTrue(box.getStyleClass().contains("tmp-status-cell"));
                assertTrue(
                        box.getChildren().stream()
                                .anyMatch(
                                        node ->
                                                node instanceof Circle circle
                                                        && circle.getStyleClass()
                                                                .contains(
                                                                        status.indicatorStyleClass())));
            }
        });
    }
}
