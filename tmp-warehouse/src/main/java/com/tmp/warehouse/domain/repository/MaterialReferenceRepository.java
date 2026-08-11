package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import java.util.List;
import java.util.Optional;

/** Persistence for warehouse-owned {@link MaterialReference} rows. */
public interface MaterialReferenceRepository {

    MaterialReference create(MaterialReference material);

    Optional<MaterialReference> findById(MaterialReferenceId id);

    Optional<MaterialReference> findByNaturalKey(
            String article, String color, String size, String unitOfMeasure);

    List<MaterialReference> findAll();
}
