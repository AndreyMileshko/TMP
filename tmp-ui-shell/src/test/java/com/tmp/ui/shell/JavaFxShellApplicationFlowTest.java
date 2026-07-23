package com.tmp.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.navigation.NavigationService;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.login.LoginViewModel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JavaFxShellApplicationFlowTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void failedLoginKeepsErrorAndDoesNotNavigate() {
        AtomicBoolean navigated = new AtomicBoolean();
        LoginViewModel viewModel = new LoginViewModel(new FailingAuth());
        viewModel.setOnLoginSuccess(() -> navigated.set(true));
        viewModel.loginProperty().set("x");
        assertFalse(viewModel.submit("bad".toCharArray()));
        assertEquals(AuthenticationFailedException.GENERIC_MESSAGE, viewModel.errorMessageProperty().get());
        assertFalse(navigated.get());
    }

    @Test
    void successfulLoginInvokesNavigationCallback() {
        AtomicBoolean navigated = new AtomicBoolean();
        SessionSummary session = new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                Login.of("admin"),
                Instant.parse("2026-07-23T04:00:00Z"));
        LoginViewModel viewModel = new LoginViewModel(new OkAuth(session));
        viewModel.setOnLoginSuccess(() -> navigated.set(true));
        viewModel.loginProperty().set("admin");
        assertTrue(viewModel.submit("ok".toCharArray()));
        assertTrue(navigated.get());
    }

    @Test
    void sceneNavigatorSwapsRootsBetweenRegisteredScreens() {
        NavigationService navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                "login",
                "fxml/fixture-screen.fxml",
                () -> new com.tmp.ui.shell.navigation.FixtureViewModel("login")));
        navigation.register(new ScreenRegistration(
                "main",
                "fxml/fixture-screen.fxml",
                () -> new com.tmp.ui.shell.navigation.FixtureViewModel("main")));
        SceneNavigator navigator = new SceneNavigator(navigation);
        javafx.scene.Scene scene = new javafx.scene.Scene(new StackPane(new Label("seed")));
        navigator.attach(scene);
        navigator.show("login");
        assertEquals("login", ((Label) scene.getRoot().lookup("#label")).getText());
        navigator.show("main");
        assertEquals("main", ((Label) scene.getRoot().lookup("#label")).getText());
    }

    @Test
    void logoutClearsAuthenticatedFlag() {
        AtomicBoolean loggedOut = new AtomicBoolean();
        AuthenticationService auth = new AuthenticationService() {
            private boolean authenticated = true;

            @Override
            public SessionSummary login(Login login, char[] password) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logout() {
                authenticated = false;
                loggedOut.set(true);
            }

            @Override
            public Optional<SessionSummary> currentSession() {
                return Optional.empty();
            }

            @Override
            public boolean isAuthenticated() {
                return authenticated;
            }
        };
        assertTrue(auth.isAuthenticated());
        if (auth.isAuthenticated()) {
            auth.logout();
        }
        assertTrue(loggedOut.get());
        assertFalse(auth.isAuthenticated());
    }

    private static final class FailingAuth implements AuthenticationService {
        @Override
        public SessionSummary login(Login login, char[] password) {
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

    private static final class OkAuth implements AuthenticationService {
        private final SessionSummary session;

        private OkAuth(SessionSummary session) {
            this.session = session;
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
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
