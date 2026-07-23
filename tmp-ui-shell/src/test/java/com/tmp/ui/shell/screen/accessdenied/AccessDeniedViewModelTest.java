package com.tmp.ui.shell.screen.accessdenied;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AccessDeniedViewModelTest {

    @Test
    void defaultMessageHasNoStackTrace() {
        AccessDeniedViewModel viewModel = new AccessDeniedViewModel();
        String message = viewModel.messageProperty().get();
        assertEquals(AccessDeniedViewModel.DEFAULT_MESSAGE, message);
        assertFalse(message.contains("Exception"));
        assertFalse(message.contains("at "));
        assertFalse(message.contains("\n\tat "));
    }

    @Test
    void customMessageStoredWithoutStackTraceLeakage() {
        AccessDeniedViewModel viewModel = new AccessDeniedViewModel();
        viewModel.setMessage("У вас нет доступа к этой операции.");
        assertEquals("У вас нет доступа к этой операции.", viewModel.messageProperty().get());
        assertFalse(viewModel.messageProperty().get().contains("at com."));
    }

    @Test
    void blankMessageFallsBackToDefault() {
        AccessDeniedViewModel viewModel = new AccessDeniedViewModel();
        viewModel.setMessage("   ");
        assertEquals(AccessDeniedViewModel.DEFAULT_MESSAGE, viewModel.messageProperty().get());
    }

    @Test
    void backRunsCallback() {
        AtomicBoolean called = new AtomicBoolean();
        AccessDeniedViewModel viewModel = new AccessDeniedViewModel();
        viewModel.setOnBack(() -> called.set(true));
        viewModel.back();
        assertTrue(called.get());
    }
}
