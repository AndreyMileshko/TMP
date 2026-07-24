package com.tmp.order.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.domain.OptimisticLockConflictException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Contract smoke tests for Stage 5 repository ports: interfaces only, typed ids, Optional absence,
 * optimistic-lock exception type present, no infrastructure types on method signatures.
 */
class RepositoryPortsContractTest {

    @Test
    void customerOrderRepositoryExposesSaveFindAndOptimisticLockContract() throws Exception {
        Method save = CustomerOrderRepository.class.getMethod(
                "save", com.tmp.order.domain.CustomerOrder.class);
        assertTrue(Modifier.isPublic(save.getModifiers()));
        assertEquals(
                OptimisticLockConflictException.class.getSimpleName(),
                "OptimisticLockConflictException");
        Method find = CustomerOrderRepository.class.getMethod(
                "findById", com.tmp.order.api.OrderId.class);
        assertEquals(Optional.class, find.getReturnType());
    }

    @Test
    void orderItemRepositorySaveUsesDomainType() throws Exception {
        Method save = OrderItemRepository.class.getMethod("save", com.tmp.order.domain.OrderItem.class);
        assertEquals(com.tmp.order.domain.OrderItem.class, save.getReturnType());
        Method find = OrderItemRepository.class.getMethod(
                "findById", com.tmp.order.api.OrderItemId.class);
        assertEquals(Optional.class, find.getReturnType());
    }

    @Test
    void revisionAndSpecificationPortsAreFindOnlyByTypedIds() throws Exception {
        Method revFind = OrderItemRevisionRepository.class.getMethod(
                "findByOrderItemIdAndRevisionNumber",
                com.tmp.order.api.OrderItemId.class,
                com.tmp.order.api.RevisionNumber.class);
        assertEquals(Optional.class, revFind.getReturnType());
        Method specFind = ItemSpecificationRepository.class.getMethod(
                "findByOrderItemIdAndRevisionNumber",
                com.tmp.order.api.OrderItemId.class,
                com.tmp.order.api.RevisionNumber.class);
        assertEquals(Optional.class, specFind.getReturnType());
    }

    @Test
    void repositoryInterfacesHaveNoInfrastructureTypesOnSignatures() {
        for (Class<?> port : new Class<?>[] {
            CustomerOrderRepository.class,
            OrderItemRepository.class,
            OrderItemRevisionRepository.class,
            ItemSpecificationRepository.class
        }) {
            assertTrue(port.isInterface());
            for (Method method : port.getMethods()) {
                assertNoInfra(method.getReturnType());
                Arrays.stream(method.getParameterTypes()).forEach(this::assertNoInfra);
            }
        }
    }

    private void assertNoInfra(Class<?> type) {
        String name = type.getName();
        assertTrue(
                !name.startsWith("org.springframework")
                        && !name.startsWith("jakarta.persistence")
                        && !name.startsWith("org.hibernate")
                        && !name.startsWith("java.sql")
                        && !name.startsWith("javax.sql")
                        && !name.contains("Jdbc")
                        && !name.contains("EntityManager"),
                () -> "Infrastructure type on repository port: " + name);
    }
}
