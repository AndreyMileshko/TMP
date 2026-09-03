package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderCustomerOptionDto;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerFilterOptionTest {

    @Test
    void composeKeepsLegacyNamedSeparateFromUnassigned() {
        List<CustomerFilterOption> options =
                CustomerFilterOption.compose(
                        List.of(
                                OrderCustomerOptionDto.unassigned(),
                                OrderCustomerOptionDto.legacyName("Парус ООО"),
                                OrderCustomerOptionDto.of("C1", "Альфа")));
        assertEquals("Без заказчика", options.getFirst().displayName());
        assertTrue(options.stream().anyMatch(option -> "Парус ООО".equals(option.displayName())));
        assertTrue(options.stream().anyMatch(option -> "Альфа".equals(option.displayName())));
        assertEquals(
                1L,
                options.stream().filter(option -> option.keys().contains(CustomerFilterKey.unassigned())).count());
    }

    @Test
    void composeMergesSameDisplayRefAndLegacyName() {
        List<CustomerFilterOption> options =
                CustomerFilterOption.compose(
                        List.of(
                                OrderCustomerOptionDto.of("CUST-1", "Альфа"),
                                OrderCustomerOptionDto.legacyName("Альфа")));
        List<CustomerFilterOption> named =
                options.stream().filter(option -> "Альфа".equals(option.displayName())).toList();
        assertEquals(1, named.size());
        assertEquals(
                Set.of(CustomerFilterKey.ref("CUST-1"), CustomerFilterKey.name("Альфа")),
                named.getFirst().keys());
    }

    @Test
    void composeDoesNotMergeDifferentRefsWithTheSameDisplayName() {
        List<CustomerFilterOption> options =
                CustomerFilterOption.compose(
                        List.of(
                                OrderCustomerOptionDto.of("CUST-A", "Альфа"),
                                OrderCustomerOptionDto.of("CUST-B", "Альфа"),
                                OrderCustomerOptionDto.legacyName("Альфа")));
        List<CustomerFilterOption> named =
                options.stream().filter(option -> "Альфа".equals(option.displayName())).toList();
        assertEquals(3, named.size());
    }

    @Test
    void selectedDisplayCountCountsOptionsNotInternalKeys() {
        List<CustomerFilterOption> options =
                CustomerFilterOption.compose(
                        List.of(
                                OrderCustomerOptionDto.of("CUST-1", "Альфа"),
                                OrderCustomerOptionDto.legacyName("Альфа")));
        Set<CustomerFilterKey> selected =
                Set.of(CustomerFilterKey.ref("CUST-1"), CustomerFilterKey.name("Альфа"));
        assertEquals(1, CustomerFilterOption.selectedDisplayCount(options, selected));
    }

    @Test
    void parseIgnoresUnknownFutureTokens() {
        assertTrue(CustomerFilterKey.parse("FUTURE:xyz").isEmpty());
        assertFalse(CustomerFilterKey.parse("REF:CUST-1").isEmpty());
        assertEquals(CustomerFilterKey.name("Парус ООО"), CustomerFilterKey.parse("NAME:Парус ООО").orElseThrow());
    }
}
