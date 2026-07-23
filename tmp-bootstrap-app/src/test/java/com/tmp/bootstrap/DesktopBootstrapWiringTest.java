package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.security.api.AuthenticationService;
import com.tmp.ui.shell.UiShellEntryPoint;
import com.tmp.ui.shell.navigation.NavigationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void securityAndUiShellBeansAreResolvable() {
        assertNotNull(uiShellEntryPoint);
        assertNotNull(navigationService);
        assertNotNull(authenticationService);
        assertNotNull(uiShellEntryPoint.sceneNavigator());
    }
}
