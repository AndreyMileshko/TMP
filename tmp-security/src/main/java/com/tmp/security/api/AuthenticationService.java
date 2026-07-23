package com.tmp.security.api;

import java.util.Optional;

/**
 * Public authentication API.
 */
public interface AuthenticationService {

    SessionSummary login(Login login, char[] password);

    void logout();

    Optional<SessionSummary> currentSession();

    boolean isAuthenticated();
}
