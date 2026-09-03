package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.order.worklist.CustomerFilterKey;
import com.tmp.ui.shell.order.worklist.CustomerFilterOption;
import java.util.List;
import java.util.Set;
import javafx.scene.control.CheckBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderListCustomerFilterPopupTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void emptyRowsDoNotResolveToSelectAllUnlessExplicitlyChecked() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            assertNull(OrderListCustomerFilterPopup.resolveSelection(false, List.of()));
            OrderListCustomerFilterPopup.Selection all =
                    OrderListCustomerFilterPopup.resolveSelection(true, List.of());
            assertTrue(all.selectAll());
            assertTrue(all.keys().isEmpty());
        });
    }

    @Test
    void allMatchOnEmptyRowsIsNotUsedForSelectAll() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            CheckBox selected = new CheckBox("Alpha");
            selected.setSelected(true);
            CheckBox unselected = new CheckBox("Beta");
            unselected.setSelected(false);
            List<OrderListCustomerFilterPopup.OptionRow> rows =
                    List.of(
                            new OrderListCustomerFilterPopup.OptionRow(
                                    new CustomerFilterOption(
                                            "Alpha", Set.of(CustomerFilterKey.ref("c-a"))),
                                    selected),
                            new OrderListCustomerFilterPopup.OptionRow(
                                    new CustomerFilterOption(
                                            "Beta", Set.of(CustomerFilterKey.ref("c-b"))),
                                    unselected));
            OrderListCustomerFilterPopup.Selection selection =
                    OrderListCustomerFilterPopup.resolveSelection(false, rows);
            assertFalse(selection.selectAll());
            assertEquals(Set.of(CustomerFilterKey.ref("c-a")), selection.keys());
        });
    }
}
