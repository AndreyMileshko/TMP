package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchFilterPreferenceCodecTest {

    @Test
    void roundTripAllPeriod() {
        ProductionWorkbenchFilterPreference preference =
                new ProductionWorkbenchFilterPreference(
                        OrderListPeriod.Preset.ALL_PERIOD, null, null);
        ProductionWorkbenchFilterPreference decoded =
                ProductionWorkbenchFilterPreferenceCodec.decode(
                        ProductionWorkbenchFilterPreferenceCodec.encode(preference));
        assertEquals(OrderListPeriod.Preset.ALL_PERIOD, decoded.periodPreset());
        assertNull(decoded.customFrom());
        assertNull(decoded.customTo());
    }

    @Test
    void roundTripCustomDates() {
        ProductionWorkbenchFilterPreference preference =
                new ProductionWorkbenchFilterPreference(
                        OrderListPeriod.Preset.CUSTOM,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31));
        ProductionWorkbenchFilterPreference decoded =
                ProductionWorkbenchFilterPreferenceCodec.decode(
                        ProductionWorkbenchFilterPreferenceCodec.encode(preference));
        assertEquals(OrderListPeriod.Preset.CUSTOM, decoded.periodPreset());
        assertEquals(LocalDate.of(2026, 1, 1), decoded.customFrom());
        assertEquals(LocalDate.of(2026, 1, 31), decoded.customTo());
    }

    @Test
    void invalidCustomFallsBackToDefaults() {
        ProductionWorkbenchFilterPreference decoded =
                ProductionWorkbenchFilterPreferenceCodec.decode(
                        "v=1;period=CUSTOM;from=2026-02-01;to=2026-01-01");
        assertEquals(OrderListPeriod.Preset.LAST_30_DAYS, decoded.periodPreset());
    }
}
