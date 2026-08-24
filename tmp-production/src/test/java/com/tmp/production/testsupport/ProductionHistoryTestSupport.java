package com.tmp.production.testsupport;

import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.domain.repository.ProductionHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Shared helpers for Production history wiring in tests. */
public final class ProductionHistoryTestSupport {

    private ProductionHistoryTestSupport() {}

    public static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T05:00:00Z"), ZoneOffset.UTC);

    public static ProductionHistoryService historyService(ProductionHistoryRepository repository) {
        return new ProductionHistoryService(repository, FIXED_CLOCK);
    }

    public static ProductionHistoryService historyService(
            ProductionHistoryRepository repository, Clock clock) {
        return new ProductionHistoryService(repository, clock);
    }

    /** No-op transaction manager for unit tests that do not need real JDBC transactions. */
    public static PlatformTransactionManager noOpTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() throws TransactionException {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition)
                    throws TransactionException {
                // no-op
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
                // no-op
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status)
                    throws TransactionException {
                // no-op
            }
        };
    }
}
