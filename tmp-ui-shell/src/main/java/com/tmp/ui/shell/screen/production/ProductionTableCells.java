package com.tmp.ui.shell.screen.production;

import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;

/**
 * Shared Production table cell helpers. Tooltip pattern matches Warehouse / Security Audit screens.
 */
public final class ProductionTableCells {

    public static final double REQUIRED_QUANTITY_MIN_WIDTH = 160;

    private ProductionTableCells() {}

    public static Tooltip fullTextTooltip(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(480);
        return tooltip;
    }

    public static <S> TableColumn<S, String> tooltipStringColumn(
            String title, double prefWidth, double minWidth, Function<S, String> value) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(value, "value");
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setPrefWidth(prefWidth);
        column.setMinWidth(minWidth);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setTooltip(null);
                                    return;
                                }
                                setText(item);
                                setTooltip(fullTextTooltip(item));
                            }
                        });
        return column;
    }
}
