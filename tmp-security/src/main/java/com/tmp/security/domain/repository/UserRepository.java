package com.tmp.security.domain.repository;

import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import java.util.List;
import java.util.Optional;

/**
 * Domain port for Security user persistence.
 */
public interface UserRepository {

    /**
     * Inserts or updates the user. Throws {@link OptimisticLockConflictException} on version conflict.
     */
    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByLoginIgnoreCase(Login login);

    boolean existsByLoginIgnoreCase(Login login);

    /** Returns true if any user row exists (any status). Used by bootstrap gate. */
    boolean existsAny();

    /**
     * Paged listing. {@code statusFilter} null means all statuses.
     */
    List<User> findPage(int pageIndex, int pageSize, UserStatus statusFilter);
}
