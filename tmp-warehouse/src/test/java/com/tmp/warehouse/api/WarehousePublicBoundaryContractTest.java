package com.tmp.warehouse.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WarehousePublicBoundaryContractTest {

    private static final Set<String> FORBIDDEN_QUERY_PREFIXES =
            Set.of("create", "execute", "send", "receive", "consume", "adjust", "post", "delete", "update");

    @Test
    void warehouseQueryApiHasNoMutatingMethodNames() {
        for (Method method : WarehouseQueryApi.class.getMethods()) {
            if (method.getDeclaringClass() != WarehouseQueryApi.class) {
                continue;
            }
            String name = method.getName();
            for (String prefix : FORBIDDEN_QUERY_PREFIXES) {
                assertTrue(
                        !name.startsWith(prefix),
                        () -> "WarehouseQueryApi must be read-only; found mutating name: " + name);
            }
        }
    }
}
