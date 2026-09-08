package com.tmp.warehouse.application;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionSummary;
import java.util.Optional;

/**
 * {@link AuthenticationService} with no current session. Used by isolated module wiring tests that
 * exercise Warehouse without a Security session. Production runtime uses the real Security bean.
 */
public final class UnauthenticatedAuthenticationService implements AuthenticationService {

    public static final UnauthenticatedAuthenticationService INSTANCE =
            new UnauthenticatedAuthenticationService();

    private UnauthenticatedAuthenticationService() {}

    @Override
    public SessionSummary login(Login login, char[] password) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public SessionSummary completePasswordSetup(
            Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
        throw new UnsupportedOperationException("not used");
    }

    @Override
    public void logout() {}

    @Override
    public Optional<SessionSummary> currentSession() {
        return Optional.empty();
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }
}
