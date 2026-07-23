package com.tmp.security.application;

import com.tmp.security.domain.Session;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application-wide holder for the current in-memory user session.
 * Thread-safe for reads; cleared on logout and shutdown.
 */
public final class SessionContext {

    private final AtomicReference<Session> current = new AtomicReference<>();

    public void open(Session session) {
        current.set(Objects.requireNonNull(session, "session"));
    }

    public void close() {
        current.set(null);
    }

    public Optional<Session> current() {
        return Optional.ofNullable(current.get());
    }

    public boolean isAuthenticated() {
        return current.get() != null;
    }
}
