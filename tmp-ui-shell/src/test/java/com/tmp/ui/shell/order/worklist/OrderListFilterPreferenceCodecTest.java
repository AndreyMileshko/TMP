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
                new OrderListFilterPreference(
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
    void dateTimePresentationFormatsLocalZone() {
        Instant instant = Instant.parse("2026-09-02T10:19:00Z");
        assertEquals(
                "02.09.2026 13:19",
                DateTimePresentation.format(instant, ZoneId.of("Europe/Moscow")));
        assertEquals("—", DateTimePresentation.customerDisplay(null));
    }
}
