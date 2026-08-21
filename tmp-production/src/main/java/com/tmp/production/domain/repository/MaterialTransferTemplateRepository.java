package com.tmp.production.domain.repository;

import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import java.util.Optional;

/** Production-owned persistence port for editable Material Transfer Templates. */
public interface MaterialTransferTemplateRepository {

    /**
     * Inserts or updates the template. On update, uses optimistic {@code version} and throws
     * {@link MaterialTransferTemplateOptimisticLockException} on conflict.
     */
    MaterialTransferTemplate save(MaterialTransferTemplate template);

    Optional<MaterialTransferTemplate> findById(MaterialTransferTemplateId templateId);
}
