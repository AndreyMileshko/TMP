package com.tmp.ui.shell.order.worklist;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;

/**
 * Reusable dot + caption graphic for operational status cells and headers. Uses dedicated
 * {@code tmp-status-dot-*} theme classes (not raw colors).
 */
public final class OperationalStatusIndicator {

    private OperationalStatusIndicator() {}

    public static HBox create(String caption, String indicatorStyleClass) {
        Objects.requireNonNull(caption, "caption");
        Objects.requireNonNull(indicatorStyleClass, "indicatorStyleClass");
        Circle dot = new Circle(6);
        dot.getStyleClass().addAll("tmp-status-dot", indicatorStyleClass);
        Label label = new Label(caption);
        label.getStyleClass().add("tmp-status-caption");
        HBox box = new HBox(8, dot, label);
        box.getStyleClass().add("tmp-status-cell");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static HBox create(OrderOperationalStatus status) {
        Objects.requireNonNull(status, "status");
        return create(status.caption(), status.indicatorStyleClass());
    }

    public static HBox create(OrderItemOperationalStatus status) {
        Objects.requireNonNull(status, "status");
        return create(status.caption(), status.indicatorStyleClass());
    }
}
