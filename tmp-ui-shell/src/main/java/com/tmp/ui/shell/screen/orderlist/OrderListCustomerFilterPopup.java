package com.tmp.ui.shell.screen.orderlist;

import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.ui.shell.theme.TmpTheme;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/**
 * Excel-like multi-select customer filter popup. Option search does not query Orders until Apply.
 */
final class OrderListCustomerFilterPopup {

    record Selection(boolean selectAll, Set<String> customerRefs, boolean includeUnassigned) {}

    private OrderListCustomerFilterPopup() {}

    static Popup show(
            javafx.scene.Node owner,
            List<OrderCustomerOptionDto> options,
            boolean selectAll,
            Set<String> selectedRefs,
            boolean includeUnassigned,
            Consumer<Selection> onApply) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox root = new VBox(8);
        root.getStyleClass().add("tmp-customer-filter-popup");
        root.setPrefWidth(280);
        root.setMaxHeight(360);
        TmpTheme.apply(root);

        Label title = new Label("Заказчики");
        title.getStyleClass().add("tmp-section-title");
        TextField search = new TextField();
        search.setPromptText("Поиск заказчика...");

        CheckBox selectAllBox = new CheckBox("Выбрать все");
        selectAllBox.setSelected(selectAll);

        VBox optionBox = new VBox(4);
        List<OptionRow> rows = new ArrayList<>();
        for (OrderCustomerOptionDto option : options) {
            CheckBox box = new CheckBox(displayName(option));
            boolean checked =
                    selectAll
                            || (option.isUnassigned() && includeUnassigned)
                            || (option.customerRef() != null && selectedRefs.contains(option.customerRef()));
            box.setSelected(checked);
            OptionRow row = new OptionRow(option, box);
            rows.add(row);
            optionBox.getChildren().add(box);
        }

        selectAllBox.selectedProperty().addListener((obs, old, selected) -> {
            if (Boolean.TRUE.equals(selected)) {
                for (OptionRow row : rows) {
                    row.box.setSelected(true);
                }
            }
        });
        for (OptionRow row : rows) {
            row.box.selectedProperty().addListener((obs, old, selected) -> {
                if (Boolean.FALSE.equals(selected)) {
                    selectAllBox.setSelected(false);
                } else if (rows.stream().allMatch(item -> item.box.isSelected())) {
                    selectAllBox.setSelected(true);
                }
            });
        }

        search.textProperty().addListener((obs, old, value) -> {
            String q = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (OptionRow row : rows) {
                boolean visible = q.isEmpty() || displayName(row.option).toLowerCase(Locale.ROOT).contains(q);
                row.box.setVisible(visible);
                row.box.setManaged(visible);
            }
        });

        ScrollPane scroll = new ScrollPane(optionBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(180);

        Button apply = new Button("Применить");
        apply.getStyleClass().add("tmp-button-action");
        Button reset = new Button("Сбросить");
        reset.getStyleClass().add("tmp-button-secondary");
        apply.setOnAction(e -> {
            boolean all = selectAllBox.isSelected() || rows.stream().allMatch(item -> item.box.isSelected());
            Set<String> refs = new LinkedHashSet<>();
            boolean unassigned = false;
            if (!all) {
                for (OptionRow row : rows) {
                    if (!row.box.isSelected()) {
                        continue;
                    }
                    if (row.option.isUnassigned()) {
                        unassigned = true;
                    } else if (row.option.customerRef() != null) {
                        refs.add(row.option.customerRef());
                    }
                }
            }
            onApply.accept(new Selection(all, refs, unassigned));
            popup.hide();
        });
        reset.setOnAction(e -> {
            selectAllBox.setSelected(true);
            for (OptionRow row : rows) {
                row.box.setSelected(true);
            }
        });

        HBox actions = new HBox(8, apply, reset);
        root.getChildren()
                .addAll(title, search, selectAllBox, new Separator(), scroll, actions);
        root.setPadding(new Insets(12));
        popup.getContent().add(root);
        var bounds = owner.localToScreen(owner.getBoundsInLocal());
        popup.show(owner, bounds.getMinX(), bounds.getMaxY() + 4);
        return popup;
    }

    private static String displayName(OrderCustomerOptionDto option) {
        if (option.isUnassigned()) {
            return "Без заказчика";
        }
        if (option.customerName() == null || option.customerName().isBlank()) {
            return "—";
        }
        return option.customerName();
    }

    private record OptionRow(OrderCustomerOptionDto option, CheckBox box) {}
}
