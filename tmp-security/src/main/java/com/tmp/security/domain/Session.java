package com.tmp.security.domain;

import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable in-memory user session. Does not contain password or hash.
 * Not persisted; effective permissions are never cached here.
 */
public final class Session {

    private final SessionId id;
    private final UserId userId;
    private final Login login;
    private final Instant startedAt;

    private Session(SessionId id, UserId userId, Login login, Instant startedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.login = Objects.requireNonNull(login, "login");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    public static Session of(SessionId id, UserId userId, Login login, Instant startedAt) {
        return new Session(id, userId, login, startedAt);
    }

    public SessionId id() {
        return id;
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
        return "Session{id=" + id + ", userId=" + userId + ", login=" + login + "}";
    }
}
