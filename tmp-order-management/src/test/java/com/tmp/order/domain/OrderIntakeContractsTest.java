package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderIntakeContractsTest {

    @Test
    void orderedQuantityRequiresPositiveWholeNumber() {
        assertEquals(BigDecimal.valueOf(8), OrderedQuantity.of(8).value());
        assertThrows(IllegalArgumentException.class, () -> OrderedQuantity.of(0));
        assertThrows(IllegalArgumentException.class, () -> OrderedQuantity.of(-1));
        assertThrows(
                IllegalArgumentException.class, () -> OrderedQuantity.of(new BigDecimal("1.5")));
    }

    @Test
    void specificationLineSupportsNullableColorAndLengthMm() {
        SpecificationLine line =
                SpecificationLine.of(
                        "ART-1",
                        "Profile",
                        "White",
                        BigDecimal.valueOf(1200),
                        BigDecimal.valueOf(16),
                        "pcs");
        assertEquals("White", line.color());
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(line.lengthMm()));

        SpecificationLine withoutOptional =
                SpecificationLine.of("ART-2", "Seal", null, null, BigDecimal.ONE, "m");
        assertEquals(null, withoutOptional.color());
        assertEquals(null, withoutOptional.lengthMm());
    }

    @Test
    void itemCommercialDataCarriesExternalPositionNumber() {
        ItemCommercialData data =
                ItemCommercialData.of(
                        ProductCode.of("WIN-1"), "Window", null, "EXT-42");
        assertEquals("EXT-42", data.externalPositionNumber());
    }

    @Test
    void incompleteDraftCommercialDataIsAllowedWithoutPlaceholders() {
        OrderCommercialData incomplete =
                OrderCommercialData.of(null, null, null, null, null, null, null);
        assertTrue(incomplete.missingMandatoryFieldsForApproval().contains("customerName"));
        assertTrue(incomplete.missingMandatoryFieldsForApproval().contains("direction"));
        assertTrue(incomplete.missingMandatoryFieldsForApproval().contains("currency"));
        assertTrue(incomplete.missingMandatoryFieldsForApproval().contains("contractRef"));
        assertTrue(incomplete.missingMandatoryFieldsForApproval().contains("siteRef"));
    }

    @Test
    void placeholderCommercialValuesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OrderCommercialData.of(
                                null,
                                "UNKNOWN",
                                null,
                                null,
                                null,
                                OrderDirection.PRIVATE,
                                CurrencyCode.of("USD")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ItemCommercialData.of(
                                ProductCode.of("P-1"), "Item", null, "IMPORT"));
    }

    @Test
    void completeCommercialDataHasNoMissingApprovalFields() {
        OrderCommercialData complete =
                OrderCommercialData.of(
                        "C-REF",
                        "Customer",
                        "CN-1",
                        "Site",
                        null,
                        OrderDirection.PRIVATE,
                        CurrencyCode.of("USD"));
        assertTrue(complete.isCompleteForApproval());
        assertEquals(List.of(), complete.missingMandatoryFieldsForApproval());
    }
}
