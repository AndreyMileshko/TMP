package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.RoleSummary;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.screen.orderlist.OrderListViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderListPermissionBootstrapIT extends AbstractBootstrapPostgresSpringTest {

    private static final char[] ADMIN_PASSWORD = "test-admin-password".toCharArray();
    private static final char[] OPERATOR_PASSWORD = "operator-order-password".toCharArray();

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private RoleAdministrationService roleAdministrationService;

    @Autowired
    private OrderListViewModel orderListViewModel;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void packagedWiringUsesAuthorizationServiceForCreateOrderPermission() {
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());

        var operatorCreation = userAdministrationService.createUser(
                Login.of("orderoperator"), DisplayName.of("Order Operator"));
        UserSummary operator = operatorCreation.user();
        RoleSummary role = roleAdministrationService.createRole("OrderViewers", "view only");
        roleAdministrationService.grantPermissionToRole(role.id(), OrderManagementPermissions.ORDER_VIEW);
        roleAdministrationService.assignRole(operator.id(), role.id());

        authenticationService.logout();
        authenticationService.completePasswordSetup(
                Login.of("orderoperator"),
                operatorCreation.activationCode(),
                OPERATOR_PASSWORD.clone(),
                OPERATOR_PASSWORD.clone());

        assertTrue(authorizationService.hasPermission(OrderManagementPermissions.ORDER_VIEW));
        assertFalse(authorizationService.hasPermission(OrderManagementPermissions.ORDER_CREATE));

        orderListViewModel.refresh();
        assertFalse(orderListViewModel.canCreateProperty().get());

        authenticationService.logout();
        authenticationService.login(Login.of("admin"), ADMIN_PASSWORD.clone());
        roleAdministrationService.grantPermissionToRole(role.id(), OrderManagementPermissions.ORDER_CREATE);

        authenticationService.logout();
        authenticationService.login(Login.of("orderoperator"), OPERATOR_PASSWORD.clone());

        assertTrue(authorizationService.hasPermission(OrderManagementPermissions.ORDER_CREATE));
        orderListViewModel.refresh();
        assertTrue(orderListViewModel.canCreateProperty().get());
    }
}
