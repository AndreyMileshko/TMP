package com.tmp.security.application;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Test double: any looked-up user id appears as ACTIVE, so authorization tests can focus on
 * permission math without wiring a full user store.
 */
final class AlwaysActiveUserRepository implements UserRepository {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    @Override
    public User save(User user) {
        return user;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.of(User.createActive(
                id, Login.of("active"), DisplayName.of("Active"), PasswordHash.of("hash"), CLOCK));
    }

    @Override
    public Optional<User> findByLoginIgnoreCase(Login login) {
        return Optional.empty();
    }

    @Override
    public boolean existsByLoginIgnoreCase(Login login) {
        return false;
    }

    @Override
    public boolean existsAny() {
        return false;
    }

    @Override
    public List<User> findPage(int pageIndex, int pageSize, UserStatus statusFilter) {
        return List.of();
    }
}
