package com.tmp.document.support;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Test-only processor that writes capability-owned JDBC state inside {@code onPost}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Test processor stores the Spring-managed JdbcTemplate used by the IT.")
public final class JdbcSideEffectDocumentProcessor implements DocumentProcessor {

    public static final String SIDE_EFFECT_TABLE = "documents.multi_document_tx_side_effects";

    private final String typeId;
    private final String ownerKey;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean failOnPost = new AtomicBoolean();
    private final AtomicBoolean observedActiveTransaction = new AtomicBoolean();

    public JdbcSideEffectDocumentProcessor(String typeId, String ownerKey, JdbcTemplate jdbcTemplate) {
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public void failOnPost() {
        failOnPost.set(true);
    }

    public boolean observedActiveTransaction() {
        return observedActiveTransaction.get();
    }

    @Override
    public String documentTypeId() {
        return typeId;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        recordTransactionState();
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        recordTransactionState();
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        recordTransactionState();
        if (failOnPost.get()) {
            throw new IllegalStateException("Simulated capability failure on post: " + ownerKey);
        }
        jdbcTemplate.update(
                "INSERT INTO " + SIDE_EFFECT_TABLE + " (document_id, owner_key, value) VALUES (?, ?, ?)",
                context.document().id(),
                ownerKey,
                "applied");
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        recordTransactionState();
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        recordTransactionState();
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        recordTransactionState();
    }

    private void recordTransactionState() {
        observedActiveTransaction.set(
                TransactionSynchronizationManager.isActualTransactionActive());
    }
}
