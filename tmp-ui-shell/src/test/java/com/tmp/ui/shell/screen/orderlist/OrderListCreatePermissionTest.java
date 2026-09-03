package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import org.junit.jupiter.api.Test;

class OrderListCreatePermissionTest {

    @Test
    void viewOnlyPermissionKeepsCreateOrderDisabled() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel(
                new FakeAuthorization(PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION)));
        viewModel.refresh();
        assertFalse(viewModel.canCreateProperty().get());
    }

    @Test
    void viewAndCreatePermissionsEnableCreateOrder() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel(
                new FakeAuthorization(
                        PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                        PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)));
        viewModel.refresh();
        assertTrue(viewModel.canCreateProperty().get());
    }

    @Test
    void missingCreatePermissionKeepsCreateOrderDisabledEvenWithOtherOrderPermissions() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel(
                new FakeAuthorization(
                        OrderManagementPermissions.ORDER_VIEW,
                        OrderManagementPermissions.ORDER_EDIT,
                        OrderManagementPermissions.ITEM_CREATE));
        viewModel.refresh();
        assertFalse(viewModel.canCreateProperty().get());
    }

    @Test
    void refreshReevaluatesPermissionsAfterSessionBecomesAvailable() {
        MutableAuthorization authorization = new MutableAuthorization();
        OrderListViewModel viewModel = OrderListTestSupport.viewModel(authorization);
        assertFalse(viewModel.canCreateProperty().get());

        authorization.grant(
                PermissionId.of(UiShellScreens.ORDER_LIST_REQUIRED_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        viewModel.refresh();
        assertTrue(viewModel.canCreateProperty().get());
    }
}
