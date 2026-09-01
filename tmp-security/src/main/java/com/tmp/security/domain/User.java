package com.tmp.security.domain;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

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
    private final boolean passwordSetupRequired;
    private final PasswordHash activationCodeHash;
    private final Instant activationCodeExpiresAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(
            UserId id,
            Login login,
            DisplayName displayName,
            PasswordHash passwordHash,
            UserStatus status,
            boolean passwordSetupRequired,
            PasswordHash activationCodeHash,
            Instant activationCodeExpiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.login = Objects.requireNonNull(login, "login");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        this.passwordSetupRequired = passwordSetupRequired;
        this.activationCodeHash = activationCodeHash;
        this.activationCodeExpiresAt = activationCodeExpiresAt;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User createActive(
            UserId id, Login login, DisplayName displayName, PasswordHash passwordHash, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new User(id, login, displayName, passwordHash, UserStatus.ACTIVE, false, null, null, 0L, now, now);
    }

    public static User createActivePendingPasswordSetup(
            UserId id, Login login, DisplayName displayName, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.instant();
        return new User(
                id,
                login,
                displayName,
                PasswordHash.uninitialized(),
                UserStatus.ACTIVE,
                true,
                null,
                null,
                0L,
                now,
                now);
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
            boolean passwordSetupRequired,
            PasswordHash activationCodeHash,
            Instant activationCodeExpiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new User(
                id,
                login,
                displayName,
                passwordHash,
                status,
                passwordSetupRequired,
                activationCodeHash,
                activationCodeExpiresAt,
                version,
                createdAt,
                updatedAt);
    }

    public User withLogin(Login newLogin, Clock clock) {
        requireActive();
        Objects.requireNonNull(newLogin, "newLogin");
        Objects.requireNonNull(clock, "clock");
        return copyWith(
                newLogin,
                displayName,
                passwordHash,
                status,
                passwordSetupRequired,
                activationCodeHash,
                activationCodeExpiresAt,
                clock.instant());
    }

    public User withDisplayName(DisplayName newDisplayName, Clock clock) {
        requireActive();
        Objects.requireNonNull(newDisplayName, "newDisplayName");
        Objects.requireNonNull(clock, "clock");
        return copyWith(
                login,
                newDisplayName,
                passwordHash,
                status,
                passwordSetupRequired,
                activationCodeHash,
                activationCodeExpiresAt,
                clock.instant());
    }

    public User withPasswordHash(PasswordHash newPasswordHash, Clock clock) {
        requireActive();
        requirePasswordSet();
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        Objects.requireNonNull(clock, "clock");
        return copyWith(
                login,
                displayName,
                newPasswordHash,
                status,
                false,
                null,
                null,
                clock.instant());
    }

    public User withPasswordInitialized(PasswordHash newPasswordHash, Clock clock) {
        requireActive();
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        Objects.requireNonNull(clock, "clock");
        if (!passwordSetupRequired) {
            throw new IllegalStateException("Password is already initialized for user: " + id);
        }
        return copyWith(
                login,
                displayName,
                newPasswordHash,
                status,
                false,
                null,
                null,
                clock.instant());
    }

    public User withActivationCode(PasswordHash codeHash, Instant expiresAt, Clock clock) {
        requireActive();
        Objects.requireNonNull(codeHash, "codeHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(clock, "clock");
        return copyWith(
                login,
                displayName,
                passwordHash,
                status,
                passwordSetupRequired,
                codeHash,
                expiresAt,
                clock.instant());
    }

    public User requiringPasswordSetup(Clock clock) {
        requireActive();
        Objects.requireNonNull(clock, "clock");
        return copyWith(
                login,
                displayName,
                PasswordHash.uninitialized(),
                status,
                true,
                null,
                null,
                clock.instant());
    }

    public User deleted(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        if (status == UserStatus.DELETED) {
            throw new UserAlreadyDeletedException("User already deleted: " + id);
        }
        return copyWith(
                login,
                displayName,
                passwordHash,
                UserStatus.DELETED,
                passwordSetupRequired,
                activationCodeHash,
                activationCodeExpiresAt,
                clock.instant());
    }

    public boolean hasActiveActivationCode(Instant now) {
        Objects.requireNonNull(now, "now");
        return activationCodeHash != null
                && activationCodeExpiresAt != null
                && !now.isAfter(activationCodeExpiresAt);
    }

    private void requireActive() {
        if (status == UserStatus.DELETED) {
            throw new UserAlreadyDeletedException("User already deleted: " + id);
        }
    }

    private void requirePasswordSet() {
        if (passwordSetupRequired) {
            throw new IllegalStateException("Password setup is required for user: " + id);
        }
    }

    private User copyWith(
            Login login,
            DisplayName displayName,
            PasswordHash passwordHash,
            UserStatus status,
            boolean passwordSetupRequired,
            PasswordHash activationCodeHash,
            Instant activationCodeExpiresAt,
            Instant updatedAt) {
        return new User(
                id,
                login,
                displayName,
                passwordHash,
                status,
                passwordSetupRequired,
                activationCodeHash,
                activationCodeExpiresAt,
                version,
                createdAt,
                updatedAt);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    public boolean passwordSetupRequired() {
        return passwordSetupRequired;
    }

    public Optional<PasswordHash> activationCodeHash() {
        return Optional.ofNullable(activationCodeHash);
    }

    public Optional<Instant> activationCodeExpiresAt() {
        return Optional.ofNullable(activationCodeExpiresAt);
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
                + ", passwordSetupRequired=" + passwordSetupRequired
                + ", version=" + version
                + "}";
    }
}
