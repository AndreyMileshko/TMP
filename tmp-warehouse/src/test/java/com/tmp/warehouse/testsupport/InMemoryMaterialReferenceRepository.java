package com.tmp.warehouse.testsupport;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link MaterialReferenceRepository} for unit tests. */
public final class InMemoryMaterialReferenceRepository implements MaterialReferenceRepository {

    private final Map<MaterialReferenceId, MaterialReference> byId = new ConcurrentHashMap<>();

    @Override
    public MaterialReference create(MaterialReference material) {
        byId.put(material.id(), material);
        return material;
    }

    @Override
    public Optional<MaterialReference> findById(MaterialReferenceId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<MaterialReference> findByNaturalKey(
            String article, String color, String size, String unitOfMeasure) {
        return byId.values().stream()
                .filter(material -> material.matchesNaturalKey(article, color, size, unitOfMeasure))
                .findFirst();
    }

    @Override
    public List<MaterialReference> findAll() {
        return List.copyOf(byId.values());
    }
}
