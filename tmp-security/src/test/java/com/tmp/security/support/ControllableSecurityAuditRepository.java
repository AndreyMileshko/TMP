package com.tmp.security.support;

import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-only audit repository that can force the next {@link #append} to fail,
 * used to prove mutation+audit share one transaction.
 */
public final class ControllableSecurityAuditRepository implements SecurityAuditRepository {

    private final SecurityAuditRepository delegate;
    private final AtomicBoolean failNextAppend = new AtomicBoolean(false);

    public ControllableSecurityAuditRepository(SecurityAuditRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public void failNextAppend() {
        failNextAppend.set(true);
    }

    @Override
    public void append(SecurityAuditEvent event) {
        if (failNextAppend.compareAndSet(true, false)) {
            throw new IllegalStateException("forced audit append failure");
        }
        delegate.append(event);
    }

    @Override
    public List<SecurityAuditEvent> findPage(AuditQueryFilter filter, int pageIndex, int pageSize) {
        return delegate.findPage(filter, pageIndex, pageSize);
    }

    @Override
    public long count(AuditQueryFilter filter) {
        return delegate.count(filter);
    }
}
