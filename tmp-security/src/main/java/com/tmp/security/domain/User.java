package com.tmp.security.domain;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Security user aggregate. Password is stored only as {@link PasswordHash};
 * plaintext never appears on this type. {@link #toString()} never exposes the hash.
 */
public final class User {

    private final UserId id;
    private final Login login;
    private final DisplayName displayName;
    private final PasswordHash passwordHash;
    private final UserStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(
            UserId id,
            Login login,
            DisplayName displayName,
            PasswordHash passwordHash,
            UserStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.login = Objects.requireNonNull(login, "login");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User createActive(
            UserId id, Login login, DisplayName displayName, PasswordHash passwordHash, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new User(id, login, displayName, passwordHash, UserStatus.ACTIVE, 0L, now, now);
    }

    /**
     * Rehydrates a persisted user. Used by persistence adapters only.
     */
    public static User rehydrate(
            UserId id,
            Login login,
            DisplayName displayName,
            PasswordHash passwordHash,
            UserStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new User(id, login, displayName, passwordHash, status, version, createdAt, updatedAt);
    }

    public User withDisplayName(DisplayName newDisplayName, Clock clock) {
        requireActive();
        Objects.requireNonNull(newDisplayName, "newDisplayName");
        Objects.requireNonNull(clock, "clock");
        return new User(
                id, login, newDisplayName, passwordHash, status, version, createdAt, clock.instant());
    }

    public User withPasswordHash(PasswordHash newPasswordHash, Clock clock) {
        requireActive();
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        Objects.requireNonNull(clock, "clock");
        return new User(
                id, login, displayName, newPasswordHash, status, version, createdAt, clock.instant());
    }

    public User deleted(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (status == UserStatus.DELETED) {
            throw new UserAlreadyDeletedException("User already deleted: " + id);
        }
        return new User(
                id, login, displayName, passwordHash, UserStatus.DELETED, version, createdAt, clock.instant());
    }

    private void requireActive() {
        if (status == UserStatus.DELETED) {
            throw new UserAlreadyDeletedException("User already deleted: " + id);
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
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

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
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
        return "User{id=" + id
                + ", login=" + login
                + ", displayName=" + displayName
                + ", passwordHash=" + passwordHash
                + ", status=" + status
                + ", version=" + version
                + "}";
    }
}
