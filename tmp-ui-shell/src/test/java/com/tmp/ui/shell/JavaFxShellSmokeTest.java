package com.tmp.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class JavaFxShellSmokeTest {

    @Test
    void windowTitleConstantDefined() {
        assertEquals("TOP Manufacturing Platform", JavaFxShellApplication.WINDOW_TITLE);
    }

    @Test
    void loginScreenIdConstantDefined() {
        assertEquals("login", UiShellEntryPoint.LOGIN_SCREEN_ID);
        assertNotNull(UiShellScreens.MAIN_SCREEN_ID);
    }
}
