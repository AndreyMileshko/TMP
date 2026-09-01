package com.tmp.ui.shell.screen.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.ShellWindowProfile;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.theme.TmpTheme;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoginControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void buttonClickPropagatesFailureMessage() throws Exception {
        LoginViewModel viewModel = new LoginViewModel(new AlwaysFailAuth());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "login",
                "com/tmp/ui/shell/screen/login/LoginScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> errorText = new AtomicReference<>();
        AtomicReference<Boolean> errorVisible = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("login");
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                TextField loginField = (TextField) root.lookup("#loginField");
                PasswordField passwordField = (PasswordField) root.lookup("#passwordField");
                Button loginButton = (Button) root.lookup("#loginButton");
                Label errorLabel = (Label) root.lookup("#errorLabel");
                loginField.setText("x");
                passwordField.setText("y");
                loginButton.fire();
                errorText.set(errorLabel.getText());
                errorVisible.set(errorLabel.isVisible());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX login failure path failed", error.get());
        }
        assertEquals(AuthenticationFailedException.GENERIC_MESSAGE, errorText.get());
        assertTrue(errorVisible.get());
        assertFalse(errorText.get().contains("at "));
    }

    @Test
    void loginFormUsesCompactBoundedFieldWidth() throws Exception {
        LoginViewModel viewModel = new LoginViewModel(new AlwaysFailAuth());
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "login",
                "com/tmp/ui/shell/screen/login/LoginScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Double> fieldWidth = new AtomicReference<>();
        AtomicReference<Double> buttonWidth = new AtomicReference<>();
        AtomicReference<Boolean> errorWraps = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("login");
                Scene scene = new Scene(root, ShellWindowProfile.LOGIN_WIDTH, ShellWindowProfile.LOGIN_HEIGHT);
                TmpTheme.apply(scene);
                root.applyCss();
                root.layout();

                TextField loginField = (TextField) root.lookup("#loginField");
                Button loginButton = (Button) root.lookup("#loginButton");
                Label errorLabel = (Label) root.lookup("#errorLabel");

                fieldWidth.set(loginField.getPrefWidth());
                buttonWidth.set(loginButton.getPrefWidth());
                errorWraps.set(errorLabel.isWrapText());

                assertTrue(loginField.getStyleClass().contains("tmp-auth-form-field"));
                assertTrue(loginButton.getStyleClass().contains("tmp-auth-form-field"));
                assertTrue(errorLabel.getStyleClass().contains("login-error"));

                loginField.setText("x");
                ((PasswordField) root.lookup("#passwordField")).setText("y");
                loginButton.fire();
                root.applyCss();
                root.layout();

                assertTrue(errorLabel.isVisible());
                assertTrue(errorLabel.isManaged());
                assertTrue(loginButton.isVisible());
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        assertTrue(fieldWidth.get() >= 240 && fieldWidth.get() <= 260);
        assertTrue(buttonWidth.get() >= 240 && buttonWidth.get() <= 260);
        assertTrue(errorWraps.get());
    }

    @Test
    void successfulSubmitInvokesOnLoginSuccessCallback() throws Exception {
        SessionSummary session = new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                Login.of("admin"),
                Instant.parse("2026-07-23T04:00:00Z"));
        AtomicBoolean success = new AtomicBoolean();
        LoginViewModel viewModel = new LoginViewModel(new AlwaysOkAuth(session));
        viewModel.setOnLoginSuccess(() -> success.set(true));
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "login",
                "com/tmp/ui/shell/screen/login/LoginScreen.fxml",
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load("login");
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                ((TextField) root.lookup("#loginField")).setText("admin");
                ((PasswordField) root.lookup("#passwordField")).setText("ok");
                ((Button) root.lookup("#loginButton")).fire();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX login success path failed", error.get());
        }
        assertTrue(success.get());
    }

    private static final class AlwaysFailAuth implements AuthenticationService {
        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new AuthenticationFailedException();
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            throw new AuthenticationFailedException();
        }

        @Override
        public void logout() {
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.empty();
        }

        @Override
        public boolean isAuthenticated() {
            return false;
        }
    }

    private static final class AlwaysOkAuth implements AuthenticationService {
        private final SessionSummary session;

        private AlwaysOkAuth(SessionSummary session) {
            this.session = session;
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
            return session;
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            return session;
        }

        @Override
        public void logout() {
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.of(session);
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }
    }
}
