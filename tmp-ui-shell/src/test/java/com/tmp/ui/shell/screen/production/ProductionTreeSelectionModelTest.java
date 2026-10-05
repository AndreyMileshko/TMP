package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionTreeSelectionModelTest {

    private static final UUID ORDER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ITEM_A1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_A2 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
    private static final UUID ITEM_A3 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3");
    private static final UUID ITEM_B1 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");

    private ProductionTreeSelectionModel model;
    private List<ProductionOrderItemRef> orderAChildren;
    private ProductionOrderItemRef refA1;
    private ProductionOrderItemRef refA2;
    private ProductionOrderItemRef refA3;
    private ProductionOrderItemRef refB1;

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @BeforeEach
    void setUp() {
        model = new ProductionTreeSelectionModel();
        refA1 = new ProductionOrderItemRef(ORDER_A, ITEM_A1);
        refA2 = new ProductionOrderItemRef(ORDER_A, ITEM_A2);
        refA3 = new ProductionOrderItemRef(ORDER_A, ITEM_A3);
        refB1 = new ProductionOrderItemRef(ORDER_B, ITEM_B1);
        orderAChildren = List.of(refA1, refA2, refA3);
    }

    @Test
    void selectOrderSelectsAllChildren() {
        model.selectAll(orderAChildren);

        assertEquals(3, model.size());
        assertTrue(model.isItemSelected(refA1));
        assertTrue(model.isItemSelected(refA2));
        assertTrue(model.isItemSelected(refA3));
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.CHECKED,
                model.orderCheckState(ORDER_A, orderAChildren));
    }

    @Test
    void deselectOrderClearsAllChildren() {
        model.selectAll(orderAChildren);
        model.deselectAll(orderAChildren);

        assertEquals(0, model.size());
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.UNCHECKED,
                model.orderCheckState(ORDER_A, orderAChildren));
    }

    @Test
    void oneChildSelectedIsIndeterminate() {
        model.setItemSelected(refA1, true);

        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.INDETERMINATE,
                model.orderCheckState(ORDER_A, orderAChildren));
    }

    @Test
    void allChildrenSelectedIsChecked() {
        model.selectAll(orderAChildren);

        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.CHECKED,
                model.orderCheckState(ORDER_A, orderAChildren));
    }

    @Test
    void unselectOneChildBecomesIndeterminate() {
        model.selectAll(orderAChildren);
        model.setItemSelected(refA2, false);

        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.INDETERMINATE,
                model.orderCheckState(ORDER_A, orderAChildren));
        assertTrue(model.isItemSelected(refA1));
        assertFalse(model.isItemSelected(refA2));
    }

    @Test
    void crossOrderSelectionRetained() {
        model.setItemSelected(refA1, true);
        model.setItemSelected(refB1, true);

        assertEquals(2, model.size());
        assertEquals(List.of(refA1, refB1), model.selectedOrderItemRefs());
    }

    @Test
    void selectedRefsSortedRegardlessOfClickOrder() {
        model.setItemSelected(refB1, true);
        model.setItemSelected(refA3, true);
        model.setItemSelected(refA1, true);

        assertEquals(List.of(refA1, refA3, refB1), model.selectedOrderItemRefs());
    }

    @Test
    void leafToggleIsBinaryNeverIndeterminate() {
        assertFalse(model.isItemSelected(refA1));
        model.setItemSelected(refA1, true);
        assertTrue(model.isItemSelected(refA1));
        model.setItemSelected(refA1, false);
        assertFalse(model.isItemSelected(refA1));
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.UNCHECKED,
                model.orderCheckState(ORDER_A, List.of(refA1)));
    }

    @Test
    void parentPartialIsIndeterminateAndSelectAllClearsIndeterminateViaChecked() {
        model.setItemSelected(refA1, true);
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.INDETERMINATE,
                model.orderCheckState(ORDER_A, orderAChildren));
        model.selectAll(orderAChildren);
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.CHECKED,
                model.orderCheckState(ORDER_A, orderAChildren));
        model.deselectAll(orderAChildren);
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.UNCHECKED,
                model.orderCheckState(ORDER_A, orderAChildren));
    }

    @Test
    void orderCheckStateUsesOnlyProvidedSelectableChildren() {
        model.setItemSelected(refA1, true);
        model.setItemSelected(refA2, true);
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.CHECKED,
                model.orderCheckState(ORDER_A, List.of(refA1, refA2)));
        assertEquals(
                ProductionTreeSelectionModel.OrderCheckState.INDETERMINATE,
                model.orderCheckState(ORDER_A, orderAChildren));
    }
}
