package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MaterialReferenceDisplayQueryContractTest {

    @Test
    void materialReferenceDisplayQueryIsReadOnlyInterface() throws Exception {
        assertTrue(MaterialReferenceDisplayQuery.class.isInterface());
        Method method =
                MaterialReferenceDisplayQuery.class.getMethod(
                        "findByMaterialCode", String.class);
        assertEquals(Optional.class, method.getReturnType());
        assertTrue(method.getName().startsWith("find"));
    }
}
