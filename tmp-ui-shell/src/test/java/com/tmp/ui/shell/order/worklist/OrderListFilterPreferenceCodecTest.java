package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderListFilterPreferenceCodecTest {

    @Test
    void roundTripPreservesSelection() {
        OrderListFilterPreference preference =
                OrderListFilterPreference.of(
                        EnumSet.of(OrderOperationalStatus.IN_PRODUCTION, OrderOperationalStatus.COMPLETED),
                        false,
                        Set.of("c-1", "c-2"),
                        true,
                        OrderListPeriod.Preset.CURRENT_MONTH,
                        null,
                        null,
                        50);
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(
                OrderListFilterPreferenceCodec.encode(preference));
        assertEquals(preference.statuses(), decoded.statuses());
        assertFalse(decoded.selectAllCustomers());
        assertEquals(Set.of("c-1", "c-2"), decoded.customerRefs());
        assertTrue(decoded.includeUnassignedCustomer());
        assertEquals(OrderListPeriod.Preset.CURRENT_MONTH, decoded.periodPreset());
    }

    @Test
    void corruptedValueFallsBackToDefaults() {
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode("%%%not-a-pref");
        OrderListFilterPreference defaults = OrderListFilterPreference.defaults();
        assertEquals(defaults.statuses(), decoded.statuses());
        assertEquals(OrderListPeriod.Preset.LAST_30_DAYS, decoded.periodPreset());
        assertTrue(decoded.selectAllCustomers());
    }

    @Test
    void staleEnumIsIgnored() {
        String raw =
                "v=1;statuses=EDITING,NOT_A_STATUS;allCustomers=true;customers=;unassigned=false;period=LAST_30_DAYS;from=;to=;pageSize=50";
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(raw);
        assertEquals(Set.of(OrderOperationalStatus.EDITING), decoded.statuses());
    }

    @Test
    void emptyStatusesFallBackToDefaults() {
        String raw =
                "v=1;statuses=;allCustomers=true;customers=;unassigned=false;period=LAST_30_DAYS;from=;to=;pageSize=50";
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(raw);
        assertEquals(OrderListFilterPreference.defaults().statuses(), decoded.statuses());
        assertFalse(decoded.statuses().contains(OrderOperationalStatus.STATUS_UNAVAILABLE));
    }

    @Test
    void statusUnavailableTokenIsIgnored() {
        String raw =
                "v=1;statuses=EDITING,STATUS_UNAVAILABLE;allCustomers=true;customers=;unassigned=false;period=LAST_30_DAYS;from=;to=;pageSize=50";
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(raw);
        assertEquals(Set.of(OrderOperationalStatus.EDITING), decoded.statuses());
    }

    @Test
    void dateTimePresentationFormatsLocalZone() {
        Instant instant = Instant.parse("2026-09-02T10:19:00Z");
        assertEquals(
                "02.09.2026 13:19",
                DateTimePresentation.format(instant, ZoneId.of("Europe/Moscow")));
        assertEquals("—", DateTimePresentation.customerDisplay(null));
    }

    @Test
    void legacyV1CustomerRefsBecomeRefKeys() {
        String raw =
                "v=1;statuses=EDITING;allCustomers=false;customers=CUST-1;unassigned=false;period=LAST_30_DAYS;from=;to=;pageSize=50";
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(raw);
        assertEquals(Set.of("CUST-1"), decoded.customerRefs());
        assertEquals(Set.of(CustomerFilterKey.ref("CUST-1")), decoded.customerKeys());
        assertFalse(decoded.includeUnassignedCustomer());
        assertFalse(decoded.selectAllCustomers());
    }

    @Test
    void version2RoundTripPreservesRefNameAndUnassigned() {
        OrderListFilterPreference preference =
                new OrderListFilterPreference(
                        EnumSet.of(OrderOperationalStatus.EDITING),
                        false,
                        Set.of(
                                CustomerFilterKey.ref("CUST-1"),
                                CustomerFilterKey.name("Парус ООО"),
                                CustomerFilterKey.unassigned()),
                        OrderListPeriod.Preset.LAST_30_DAYS,
                        null,
                        null,
                        50);
        OrderListFilterPreference decoded =
                OrderListFilterPreferenceCodec.decode(OrderListFilterPreferenceCodec.encode(preference));
        assertEquals(preference.customerKeys(), decoded.customerKeys());
        assertEquals(Set.of("CUST-1"), decoded.customerRefs());
        assertEquals(Set.of("Парус ООО"), decoded.customerNames());
        assertTrue(decoded.includeUnassignedCustomer());
    }

    @Test
    void unknownFutureCustomerKeyIsIgnored() {
        String raw =
                "v=2;statuses=EDITING;allCustomers=false;customers=REF:CUST-1,FUTURE:xyz;unassigned=false;period=LAST_30_DAYS;from=;to=;pageSize=50";
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(raw);
        assertEquals(Set.of(CustomerFilterKey.ref("CUST-1")), decoded.customerKeys());
    }

    @Test
    void unknownVersionFallsBackToDefaults() {
        String raw =
                "v=99;statuses=EDITING;allCustomers=false;customers=REF:CUST-1;unassigned=false;period=LAST_30_DAYS;from=;to=;pageSize=50";
        OrderListFilterPreference decoded = OrderListFilterPreferenceCodec.decode(raw);
        assertTrue(decoded.selectAllCustomers());
        assertTrue(decoded.customerKeys().isEmpty());
    }
}
