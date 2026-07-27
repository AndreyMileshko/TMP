package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.security.api.AuthenticationService;
import com.tmp.ui.shell.UiShellEntryPoint;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.screen.ordereditor.OrderEditorViewModel;
import com.tmp.ui.shell.screen.orderlist.OrderListViewModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DesktopBootstrapWiringTest extends AbstractBootstrapPostgresSpringTest {

    @Autowired
    private UiShellEntryPoint uiShellEntryPoint;

    @Autowired
    private NavigationService navigationService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void securityAndUiShellBeansAreResolvable() {
        assertNotNull(uiShellEntryPoint);
        assertNotNull(navigationService);
        assertNotNull(authenticationService);
        assertNotNull(uiShellEntryPoint.sceneNavigator());
    }

    @Test
    void orderListAndEditorAreWiredToQueryAndDocumentUiServices() {
        assertNotNull(applicationContext.getBean(OrderQueryService.class));
        assertNotNull(applicationContext.getBean(OrderDocumentUiService.class));
        assertNotNull(applicationContext.getBean(OrderListViewModel.class));
        assertNotNull(applicationContext.getBean(OrderEditorViewModel.class));
    }
}
