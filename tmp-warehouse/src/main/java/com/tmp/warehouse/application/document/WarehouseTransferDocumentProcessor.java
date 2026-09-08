package com.tmp.warehouse.application.document;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.warehouse.domain.repository.WarehouseTransferDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/**
 * Document Engine processor for {@code warehouse.transfer} (Stage 3.5.2 foundation).
 *
 * <p>POST / physical send is deferred to a later substage. DELETE explicitly removes the
 * Warehouse-owned payload (no cross-schema FK to {@code documents.documents}).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed repository.")
public final class WarehouseTransferDocumentProcessor implements DocumentProcessor {

    public static final String DOCUMENT_TYPE_ID = "warehouse.transfer";

    private final WarehouseTransferDocumentRepository repository;

    public WarehouseTransferDocumentProcessor(WarehouseTransferDocumentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String documentTypeId() {
        return DOCUMENT_TYPE_ID;
    }

    @Override
    public void validateCreate(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void validateUpdate(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
    }

    @Override
    public void onPost(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        throw new UnsupportedOperationException(
                "Warehouse Transfer Document POST (physical send) is not implemented in Stage 3.5.2");
    }

    @Override
    public void onUnpost(DocumentOperationContext context) {
        throw new UnsupportedOperationException(
                "Warehouse Transfer Document does not support UNPOST");
    }

    @Override
    public void onClose(DocumentOperationContext context) {
        throw new UnsupportedOperationException(
                "Warehouse Transfer Document does not support CLOSE");
    }

    @Override
    public void onDelete(DocumentOperationContext context) {
        Objects.requireNonNull(context, "context");
        repository.deleteByDocumentId(context.document().id());
    }
}
