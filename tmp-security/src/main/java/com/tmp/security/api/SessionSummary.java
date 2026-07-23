package com.tmp.security.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Display-safe session summary. Never carries password or hash.
 */
public final class SessionSummary {

    private final SessionId sessionId;
    private final UserId userId;
    private final Login login;
    private final Instant startedAt;

    public SessionSummary(SessionId sessionId, UserId userId, Login login, Instant startedAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.login = Objects.requireNonNull(login, "login");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public UserId userId() {
        return userId;
    }

    public Login login() {
        return login;
    }

    public Instant startedAt() {
        return startedAt;
    }

    @Override
    public String toString() {
        return "SessionSummary{sessionId=" + sessionId + ", userId=" + userId + ", login=" + login + "}";
    }
}
