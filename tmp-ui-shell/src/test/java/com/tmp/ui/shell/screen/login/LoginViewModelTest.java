package com.tmp.ui.shell.screen.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PasswordSetupRequiredException;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginViewModelTest {

    @Test
    void successfulLoginClearsErrorAndStoresSession() {
        SessionSummary session = new SessionSummary(
                SessionId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                Login.of("admin"),
                Instant.parse("2026-07-23T04:00:00Z"));
        LoginViewModel viewModel = new LoginViewModel(new FakeAuth(session, null));
        viewModel.loginProperty().set("admin");

        assertEquals(LoginViewModel.SubmitOutcome.SUCCESS, viewModel.submit("secret".toCharArray()));
        assertEquals("", viewModel.errorMessageProperty().get());
        assertEquals(session, viewModel.lastSession().orElseThrow());
    }

    @Test
    void failedLoginSetsGenericMessageWithoutStackTraceLeakage() {
        LoginViewModel viewModel = new LoginViewModel(new FakeAuth(null, new AuthenticationFailedException()));
        viewModel.loginProperty().set("nobody");

        assertEquals(LoginViewModel.SubmitOutcome.FAILED, viewModel.submit("wrong".toCharArray()));
        assertEquals(AuthenticationFailedException.GENERIC_MESSAGE, viewModel.errorMessageProperty().get());
        assertFalse(viewModel.errorMessageProperty().get().contains("Exception"));
        assertFalse(viewModel.errorMessageProperty().get().contains("at "));
        assertTrue(viewModel.lastSession().isEmpty());
    }

    @Test
    void passwordSetupRequiredOutcomeDoesNotOpenSession() {
        LoginViewModel viewModel = new LoginViewModel(new FakeAuth(null, null, Login.of("newbie")));
        viewModel.loginProperty().set("newbie");

        assertEquals(LoginViewModel.SubmitOutcome.PASSWORD_SETUP_REQUIRED, viewModel.submit("x".toCharArray()));
        assertEquals("newbie", viewModel.pendingPasswordSetupLogin().orElseThrow().value());
        assertTrue(viewModel.lastSession().isEmpty());
    }

    private static final class FakeAuth implements AuthenticationService {

        private final SessionSummary success;
        private final AuthenticationFailedException failure;
        private final Login setupRequiredLogin;

        private FakeAuth(SessionSummary success, AuthenticationFailedException failure) {
            this(success, failure, null);
        }

        private FakeAuth(SessionSummary success, AuthenticationFailedException failure, Login setupRequiredLogin) {
            this.success = success;
            this.failure = failure;
            this.setupRequiredLogin = setupRequiredLogin;
        }

        @Override
        public SessionSummary login(Login login, char[] password) {
            if (setupRequiredLogin != null) {
                throw new PasswordSetupRequiredException(setupRequiredLogin);
            }
            if (failure != null) {
                throw failure;
            }
            return success;
        }

        @Override
        public SessionSummary completePasswordSetup(Login login, char[] newPassword, char[] confirmPassword) {
            if (failure != null) {
                throw failure;
            }
            return success;
        }

        @Override
        public void logout() {
        }

        @Override
        public Optional<SessionSummary> currentSession() {
            return Optional.ofNullable(success);
        }

        @Override
        public boolean isAuthenticated() {
            return success != null;
        }
    }
}
