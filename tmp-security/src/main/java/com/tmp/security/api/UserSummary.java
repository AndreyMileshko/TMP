package com.tmp.security.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Display-safe user summary. Never carries password or hash.
 */
public final class UserSummary {

    private final UserId id;
    private final Login login;
    private final DisplayName displayName;
    private final String status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public UserSummary(
            UserId id,
            Login login,
            DisplayName displayName,
            String status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.login = Objects.requireNonNull(login, "login");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public UserId id() {
        return id;
    }

    public Login login() {
        return login;
    }

    public DisplayName displayName() {
        return displayName;
    }

    public String status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "UserSummary{id=" + id + ", login=" + login + ", status=" + status + "}";
    }
}
