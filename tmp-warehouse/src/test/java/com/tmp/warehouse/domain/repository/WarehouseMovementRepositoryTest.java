package com.tmp.warehouse.domain.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WarehouseMovementRepositoryTest {

    @Test
    void repositoryExposesAppendOnlyWritePath() {
        boolean hasAppend =
                Arrays.stream(WarehouseMovementRepository.class.getMethods())
                        .anyMatch(method -> method.getName().equals("append"));
        boolean hasUpdate =
                Arrays.stream(WarehouseMovementRepository.class.getMethods())
                        .anyMatch(method -> method.getName().startsWith("update"));
        boolean hasDelete =
                Arrays.stream(WarehouseMovementRepository.class.getMethods())
                        .anyMatch(method -> method.getName().startsWith("delete"));

        assertTrue(hasAppend);
        assertTrue(!hasUpdate, "Movement repository must not expose update");
        assertTrue(!hasDelete, "Movement repository must not expose delete");
    }

    @Test
    void repositorySupportsHistoryRead() {
        boolean hasHistory =
                Arrays.stream(WarehouseMovementRepository.class.getMethods())
                        .anyMatch(method -> method.getName().equals("findHistoryByStockPosition"));
        assertTrue(hasHistory);
    }
}
