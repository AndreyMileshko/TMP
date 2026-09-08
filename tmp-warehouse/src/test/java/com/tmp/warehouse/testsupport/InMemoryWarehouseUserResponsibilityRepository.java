package com.tmp.warehouse.testsupport;

import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory responsibility store for unit tests. */
public final class InMemoryWarehouseUserResponsibilityRepository
        implements WarehouseUserResponsibilityRepository {

    private final Set<String> pairs = ConcurrentHashMap.newKeySet();

    @Override
    public void assign(UUID userId, WarehouseId warehouseId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        pairs.add(key(userId, warehouseId));
    }

    @Override
    public void remove(UUID userId, WarehouseId warehouseId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        pairs.remove(key(userId, warehouseId));
    }

    @Override
    public boolean isResponsible(UUID userId, WarehouseId warehouseId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        return pairs.contains(key(userId, warehouseId));
    }

    @Override
    public List<WarehouseId> listWarehouseIdsForUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        List<WarehouseId> result = new ArrayList<>();
        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts[1].equals(userId.toString())) {
                result.add(WarehouseId.of(UUID.fromString(parts[0])));
            }
        }
        result.sort(Comparator.comparing(id -> id.value()));
        return List.copyOf(result);
    }

    @Override
    public List<UUID> listUserIdsForWarehouse(WarehouseId warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        List<UUID> result = new ArrayList<>();
        for (String pair : pairs) {
            String[] parts = pair.split(":", 2);
            if (parts[0].equals(warehouseId.value().toString())) {
                result.add(UUID.fromString(parts[1]));
            }
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static String key(UUID userId, WarehouseId warehouseId) {
        return warehouseId.value() + ":" + userId;
    }
}
