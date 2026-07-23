package com.tmp.security.application;

import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionSummary;
import java.util.Objects;
import java.util.Optional;

public final class DefaultAuthenticationService implements AuthenticationService {

    private final AuthenticationApplicationService delegate;

    public DefaultAuthenticationService(AuthenticationApplicationService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public SessionSummary login(Login login, char[] password) {
        return SecurityApiMapper.toSummary(delegate.login(login, password));
    }

    @Override
    public void logout() {
        delegate.logout();
    }

    @Override
    public Optional<SessionSummary> currentSession() {
        return delegate.currentSession().map(SecurityApiMapper::toSummary);
    }

    @Override
    public boolean isAuthenticated() {
        return delegate.isAuthenticated();
    }
}
