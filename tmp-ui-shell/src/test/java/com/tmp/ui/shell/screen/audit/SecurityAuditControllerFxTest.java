package com.tmp.ui.shell.screen.audit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuditEventSummary;
import com.tmp.security.api.AuditQueryService;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SecurityAuditControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadsTableAgainstViewModel() throws Exception {
        SecurityAuditViewModel viewModel = new SecurityAuditViewModel(new EmptyAudit());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "audit",
                "com/tmp/ui/shell/screen/audit/SecurityAuditScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<TableView<?>> table = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("audit");
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                table.set((TableView<?>) root.lookup("#auditTable"));
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
        assertNotNull(table.get());
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
}
