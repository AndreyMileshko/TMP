package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderQueryApiContractTest {

    @Test
    void orderQueryServiceIsReadOnlyInterface() {
        assertTrue(OrderQueryService.class.isInterface());
        for (Method method : OrderQueryService.class.getMethods()) {
            String name = method.getName();
            assertFalse(
                    name.startsWith("create")
                            || name.startsWith("update")
                            || name.startsWith("delete")
                            || name.startsWith("approve")
                            || name.startsWith("cancel")
                            || name.startsWith("save"),
                    () -> "Mutating method on Public Query API: " + name);
            assertFalse(
                    method.getReturnType().getName().startsWith("com.tmp.order.domain"),
                    () -> "Domain type leaked in return: " + method);
            Arrays.stream(method.getParameterTypes())
                    .forEach(type -> assertFalse(
                            type.getName().startsWith("com.tmp.order.domain"),
                            () -> "Domain type leaked in parameter: " + method));
        }
    }

    @Test
    void queryServiceDoesNotExposeDraftAccessors() {
        for (Method method : OrderQueryService.class.getMethods()) {
            String lower = method.getName().toLowerCase();
            assertFalse(lower.contains("draft"), () -> "Draft accessor on public API: " + method);
        }
        for (Method method : OrderItemDto.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String lower = method.getName().toLowerCase();
            assertFalse(
                    lower.contains("draft"),
                    () -> "Draft field on public OrderItemDto: " + method.getName());
        }
    }

    @Test
    void absenceIsOptionalNotNullContract() throws Exception {
        assertEquals(
                Optional.class,
                OrderQueryService.class.getMethod("getOrder", OrderId.class).getReturnType());
        assertEquals(
                Optional.class,
                OrderQueryService.class
                        .getMethod("getOrderItem", OrderItemId.class)
                        .getReturnType());
        assertEquals(
                Optional.class,
                OrderQueryService.class
                        .getMethod(
                                "getOrderItemRevision", OrderItemId.class, RevisionNumber.class)
                        .getReturnType());
        assertEquals(
                Optional.class,
                OrderQueryService.class
                        .getMethod("getActiveOrderItemRevision", OrderItemId.class)
                        .getReturnType());
        assertEquals(
                Optional.class,
                OrderQueryService.class
                        .getMethod(
                                "getItemSpecification", OrderItemId.class, RevisionNumber.class)
                        .getReturnType());
    }

    @Test
    void revisionDtoRejectsDraftStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemRevisionDto.of(
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        RevisionStatus.DRAFT,
                        BigDecimal.ONE,
                        null));
    }

    @Test
    void revisionDtoAcceptsApprovedOnly() {
        OrderItemRevisionDto dto = OrderItemRevisionDto.of(
                OrderItemId.generate(),
                RevisionNumber.first(),
                RevisionStatus.APPROVED,
                BigDecimal.TEN,
                null);
        assertEquals(RevisionStatus.APPROVED, dto.status());
    }

    @Test
    void dtosHaveNoForeignCapabilityFields() {
        for (Class<?> dto : List.of(
                OrderDto.class,
                OrderSummaryDto.class,
                OrderItemDto.class,
                OrderItemRevisionDto.class,
                ItemSpecificationDto.class,
                SpecificationLineDto.class)) {
            for (Method method : dto.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())
                        || method.getParameterCount() > 0
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String lower = method.getName().toLowerCase();
                assertFalse(
                        lower.contains("production")
                                || lower.contains("stock")
                                || lower.contains("cutting")
                                || lower.contains("warehouse")
                                || lower.contains("launched")
                                || lower.contains("released")
                                || lower.contains("batch")
                                || lower.contains("lot")
                                || lower.contains("reserve"),
                        () -> "Foreign capability field on " + dto.getSimpleName() + ": "
                                + method.getName());
            }
        }
    }

    @Test
    void specificationLinesCollectionIsUnmodifiable() {
        List<SpecificationLineDto> mutable = new ArrayList<>();
        mutable.add(SpecificationLineDto.of(
                "M1", "Glass", null, null, BigDecimal.ONE, "m2"));
        ItemSpecificationDto dto = ItemSpecificationDto.of(
                OrderItemId.generate(), RevisionNumber.first(), mutable);
        mutable.add(SpecificationLineDto.of(
                "M2", "Wood", null, null, BigDecimal.ONE, "m"));
        assertEquals(1, dto.lines().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> dto.lines()
                        .add(SpecificationLineDto.of(
                                "M3", "X", null, null, BigDecimal.ONE, "pcs")));
    }

    @Test
    void orderDtoAllowsIncompleteDraftCommercialFields() {
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        OrderDto dto =
                OrderDto.of(
                        OrderId.generate(),
                        "ORD-INCOMPLETE",
                        OrderStatus.DRAFT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        now,
                        now);
        assertEquals(OrderStatus.DRAFT, dto.status());
        assertEquals(null, dto.customerName());
        assertEquals(null, dto.direction());
        assertEquals(null, dto.currency());
    }

    @Test
    void orderSummaryDtoAllowsNullCustomerNameForIncompleteDraft() {
        Instant now = Instant.parse("2026-07-30T10:00:00Z");
        OrderSummaryDto summary =
                OrderSummaryDto.of(
                        OrderId.generate(), "ORD-INCOMPLETE", OrderStatus.DRAFT, null, null, now);
        assertEquals(null, summary.customerName());
    }

    @Test
    void orderItemDtoExposesOnlyActiveRevisionPointer() {
        Instant now = Instant.parse("2026-07-25T10:00:00Z");
        OrderItemDto dto = OrderItemDto.of(
                OrderItemId.generate(),
                OrderId.generate(),
                "P-1",
                "Door",
                null,
                "POS-1",
                OrderItemStatus.ACTIVE,
                RevisionNumber.first(),
                now,
                now);
        assertTrue(dto.activeRevisionNumber().isPresent());
        assertEquals(1, dto.activeRevisionNumber().orElseThrow().value());
    }

    @Test
    void pageResultContentIsUnmodifiable() {
        PageResult<OrderSummaryDto> page = PageResult.of(List.of(), 0, 50, 0L);
        assertThrows(UnsupportedOperationException.class, () -> page.content().clear());
    }
}
