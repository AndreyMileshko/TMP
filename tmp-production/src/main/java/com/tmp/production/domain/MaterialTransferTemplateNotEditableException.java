package com.tmp.production.domain;

import java.util.Objects;

/** Raised when a confirmed Material Transfer Template is edited. */
public final class MaterialTransferTemplateNotEditableException extends RuntimeException {

    private final MaterialTransferTemplateId templateId;
    private final MaterialTransferTemplateStatus status;

    public MaterialTransferTemplateNotEditableException(
            MaterialTransferTemplateId templateId, MaterialTransferTemplateStatus status) {
        super(
                "Material transfer template is not editable: templateId="
                        + Objects.requireNonNull(templateId, "templateId")
                        + ", status="
                        + Objects.requireNonNull(status, "status"));
        this.templateId = templateId;
        this.status = status;
    }

    public MaterialTransferTemplateId templateId() {
        return templateId;
    }

    public MaterialTransferTemplateStatus status() {
        return status;
    }
}
