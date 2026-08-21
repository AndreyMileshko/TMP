package com.tmp.production.domain;

/** Raised when a concurrent edit updates a Material Transfer Template with a stale version. */
public final class MaterialTransferTemplateOptimisticLockException extends RuntimeException {

    private final MaterialTransferTemplateId templateId;
    private final long expectedVersion;

    public MaterialTransferTemplateOptimisticLockException(
            MaterialTransferTemplateId templateId, long expectedVersion) {
        super(
                "Optimistic lock failure for material transfer template "
                        + templateId
                        + ", expectedVersion="
                        + expectedVersion);
        this.templateId = templateId;
        this.expectedVersion = expectedVersion;
    }

    public MaterialTransferTemplateId templateId() {
        return templateId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
