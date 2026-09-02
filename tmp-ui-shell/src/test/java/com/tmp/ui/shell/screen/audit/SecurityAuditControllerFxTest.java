package com.tmp.ui.shell.screen.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.theme.TmpTheme;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SecurityAuditControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsStandardAuditScreenStructure() throws Exception {
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(new EmptyAudit());
        Parent root = loadScreen(viewModel, 1024, 700);

        assertTrue(root.getStyleClass().contains("tmp-screen"));
        assertNotNull(findByStyleClass(root, "tmp-screen-title"));
        assertEquals("Аудит безопасности", labelText(findByStyleClass(root, "tmp-screen-title")));
        assertNotNull(findByStyleClass(root, "tmp-screen-subtitle"));
        assertEquals(
                "Журнал событий безопасности и административных действий",
                labelText(findByStyleClass(root, "tmp-screen-subtitle")));

        TableView<?> table = (TableView<?>) root.lookup("#auditTable");
        assertNotNull(table);
        assertEquals(-1.0, table.getPrefHeight(), 0.01);
        assertNotNull(root.lookup("#fromDatePicker"));
        assertNotNull(root.lookup("#toDatePicker"));
        assertNotNull(root.lookup("#operationFilterField"));
        assertNotNull(root.lookup("#applyFilterButton"));
        assertNotNull(root.lookup("#resetFilterButton"));
        assertNotNull(root.lookup("#previousPageButton"));
        assertNotNull(root.lookup("#nextPageButton"));
        assertNotNull(root.lookup("#pageLabel"));

        Label errorLabel = (Label) root.lookup("#errorLabel");
        assertNotNull(errorLabel);
        assertTrue(errorLabel.getStyleClass().contains("tmp-message-error"));

        Button previous = (Button) root.lookup("#previousPageButton");
        Button next = (Button) root.lookup("#nextPageButton");
        assertTrue(previous.isDisable());
        assertTrue(next.isDisable());
        assertEquals("← Назад", previous.getText());
        assertEquals("Вперёд →", next.getText());
    }

    @Test
    void filterAndPaginationControlsWork() throws Exception {
        RecordingAudit audit = new RecordingAudit();
        for (int i = 0; i < 25; i++) {
            audit.events.add(sampleEvent("USER_CREATED"));
        }
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(audit);
        Parent root = loadScreen(viewModel, 1024, 700);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TextField operationField = (TextField) root.lookup("#operationFilterField");
                DatePicker fromPicker = (DatePicker) root.lookup("#fromDatePicker");
                DatePicker toPicker = (DatePicker) root.lookup("#toDatePicker");
                Button apply = (Button) root.lookup("#applyFilterButton");
                Button reset = (Button) root.lookup("#resetFilterButton");
                Button next = (Button) root.lookup("#nextPageButton");
                Label pageLabel = (Label) root.lookup("#pageLabel");

                assertEquals("Страница 1 · 25 событий", pageLabel.getText());
                assertFalse(next.isDisable());

                operationField.setText("USER_CREATED");
                fromPicker.setValue(java.time.LocalDate.of(2026, 1, 1));
                toPicker.setValue(java.time.LocalDate.of(2026, 12, 31));
                apply.fire();
                assertEquals("USER_CREATED", audit.lastOperation);
                assertEquals(0, viewModel.pageIndexProperty().get());

                next.fire();
                assertEquals(1, viewModel.pageIndexProperty().get());
                assertEquals("Страница 2 · 25 событий", pageLabel.getText());

                reset.fire();
                assertEquals("", operationField.getText());
                assertEquals(null, fromPicker.getValue());
                assertEquals(null, toPicker.getValue());
                assertEquals(0, viewModel.pageIndexProperty().get());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Security audit FX interaction failed", error.get());
        }
    }

    @Test
    void pageLabelHasNoMojibake() throws Exception {
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(new EmptyAudit());
        Parent root = loadScreen(viewModel, 1024, 700);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> pageText = new AtomicReference<>();
        Platform.runLater(() -> {
            pageText.set(((Label) root.lookup("#pageLabel")).getText());
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals("Страница 1 · 0 событий", pageText.get());
        assertFalse(pageText.get().contains("РЎ"));
    }

    private static Parent loadScreen(SecurityAuditViewModel viewModel, int width, int height) throws Exception {
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "audit",
                "com/tmp/ui/shell/screen/audit/SecurityAuditScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> root = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Parent loaded = navigation.load("audit");
                Stage stage = new Stage();
                Scene scene = new Scene(loaded, width, height);
                TmpTheme.apply(scene);
                stage.setScene(scene);
                loaded.applyCss();
                loaded.layout();
                root.set(loaded);
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Security audit FX load failed", error.get());
        }
        return root.get();
    }

    private static Label findByStyleClass(Parent root, String styleClass) {
        for (var node : root.lookupAll("." + styleClass)) {
            if (node instanceof Label label) {
                return label;
            }
        }
        throw new IllegalStateException("Label with style class not found: " + styleClass);
    }

    private static String labelText(Label label) {
        return label.getText();
    }

    private static AuditEventSummary sampleEvent(String operation) {
        return new AuditEventSummary(
                com.tmp.security.api.AuditEventId.of(java.util.UUID.randomUUID()),
                Instant.parse("2026-07-23T04:00:00Z"),
                null,
                "admin",
                operation,
                "USER",
                "admin",
                "Описание события",
                "SUCCESS");
    }

    private static final class EmptyAudit implements AuditQueryService {
        @Override
        public List<AuditEventSummary> queryAuditEvents(
                Instant from, Instant to, UserId actorUserId, String operation, int pageIndex, int pageSize) {
            return List.of();
        }

        @Override
        public long countAuditEvents(Instant from, Instant to, UserId actorUserId, String operation) {
            return 0L;
        }
    }

    private static final class RecordingAudit implements AuditQueryService {
        private final java.util.List<AuditEventSummary> events = new java.util.ArrayList<>();
        private String lastOperation;

        @Override
        public List<AuditEventSummary> queryAuditEvents(
                Instant from, Instant to, UserId actorUserId, String operation, int pageIndex, int pageSize) {
            lastOperation = operation;
            int fromIdx = pageIndex * SecurityAuditViewModel.PAGE_SIZE;
            int toIdx = Math.min(events.size(), fromIdx + SecurityAuditViewModel.PAGE_SIZE);
            if (fromIdx >= events.size()) {
                return List.of();
            }
            return events.subList(fromIdx, toIdx);
        }

        @Override
        public long countAuditEvents(Instant from, Instant to, UserId actorUserId, String operation) {
            lastOperation = operation;
            return events.size();
        }
    }
}
