package com.tmp.security.support;

import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test-only {@link UserRepository} that can pause on {@link #findById(UserId)} so a concurrent
 * logical delete can win deterministically between credential verification and session open.
 */
public final class ControllableUserRepository implements UserRepository {

    private final UserRepository delegate;
    private final AtomicReference<CountDownLatch> enteredFindById = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> releaseFindById = new AtomicReference<>();

    public ControllableUserRepository(UserRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public void armFindByIdBarrier(CountDownLatch entered, CountDownLatch release) {
        enteredFindById.set(Objects.requireNonNull(entered, "entered"));
        releaseFindById.set(Objects.requireNonNull(release, "release"));
    }

    public void clearFindByIdBarrier() {
        enteredFindById.set(null);
        releaseFindById.set(null);
    }

    @Override
    public User save(User user) {
        return delegate.save(user);
    }

    @Override
    public Optional<User> findById(UserId id) {
        CountDownLatch entered = enteredFindById.get();
        CountDownLatch release = releaseFindById.get();
        if (entered != null && release != null) {
            entered.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting for findById barrier release");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted waiting for findById barrier release", ex);
            }
        }
        return delegate.findById(id);
    }

    @Override
    public Optional<User> findByLoginIgnoreCase(Login login) {
        return delegate.findByLoginIgnoreCase(login);
    }

    @Override
    public boolean existsByLoginIgnoreCase(Login login) {
        return delegate.existsByLoginIgnoreCase(login);
    }

    @Override
    public boolean existsAny() {
        return delegate.existsAny();
    }

    @Override
    public List<User> findPage(int pageIndex, int pageSize, UserStatus statusFilter) {
        return delegate.findPage(pageIndex, pageSize, statusFilter);
    }
}
