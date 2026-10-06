package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseDemandStatusDeriverTest {

    @Test
    void fulfilledWhenReceivedCoversRequired() {
        assertEquals(
                WarehouseDemandDerivedStatus.FULFILLED,
                WarehouseDemandStatusDeriver.deriveLineStatus(
                        false, new BigDecimal("10"), new BigDecimal("10"), false));
    }

    @Test
    void inFulfillmentRequiresActiveTransferNotJustPartialReceive() {
        assertEquals(
                WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY,
                WarehouseDemandStatusDeriver.deriveLineStatus(
                        false, new BigDecimal("10"), new BigDecimal("4"), false));
        assertEquals(
                WarehouseDemandDerivedStatus.IN_FULFILLMENT,
                WarehouseDemandStatusDeriver.deriveLineStatus(
                        false, new BigDecimal("10"), new BigDecimal("4"), true));
    }

    @Test
    void cancelledHeaderTakesPrecedence() {
        assertEquals(
                WarehouseDemandDerivedStatus.CANCELLED,
                WarehouseDemandStatusDeriver.deriveLineStatus(
                        true, new BigDecimal("10"), new BigDecimal("10"), true));
    }

    @Test
    void headerAggregatesMixedLines() {
        assertEquals(
                WarehouseDemandDerivedStatus.IN_FULFILLMENT,
                WarehouseDemandStatusDeriver.deriveHeaderStatus(
                        false,
                        List.of(
                                WarehouseDemandDerivedStatus.FULFILLED,
                                WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY,
                                WarehouseDemandDerivedStatus.IN_FULFILLMENT)));
    }

    @Test
    void headerAllFulfilled() {
        assertEquals(
                WarehouseDemandDerivedStatus.FULFILLED,
                WarehouseDemandStatusDeriver.deriveHeaderStatus(
                        false,
                        List.of(
                                WarehouseDemandDerivedStatus.FULFILLED,
                                WarehouseDemandDerivedStatus.FULFILLED)));
    }

    @Test
    void effectiveWaitingReasonOnlyWhenWaiting() {
        assertEquals(
                WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK,
                WarehouseDemandStatusDeriver.effectiveWaitingReason(
                        WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY,
                        WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK));
        assertEquals(
                WarehouseDemandWaitingReason.ROUTING_DEFERRED,
                WarehouseDemandStatusDeriver.effectiveWaitingReason(
                        WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY, null));
        assertNull(
                WarehouseDemandStatusDeriver.effectiveWaitingReason(
                        WarehouseDemandDerivedStatus.IN_FULFILLMENT,
                        WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK));
    }

    @Test
    void remainingClampsAtZeroOnOverReceipt() {
        assertEquals(
                0,
                WarehouseDemandStatusDeriver.remainingQuantity(
                                new BigDecimal("10"), new BigDecimal("12"))
                        .compareTo(BigDecimal.ZERO));
    }

    @Test
    void activeTransferRules() {
        assertEquals(true, WarehouseDemandActiveTransferRules.isActive("DRAFT", null));
        assertEquals(
                true, WarehouseDemandActiveTransferRules.isActive("POSTED", "AWAITING_RECEIPT"));
        assertEquals(false, WarehouseDemandActiveTransferRules.isActive("POSTED", "RETURN_PENDING"));
        assertEquals(false, WarehouseDemandActiveTransferRules.isActive("POSTED", "SETTLED"));
        assertEquals(false, WarehouseDemandActiveTransferRules.isActive("CLOSED", "SETTLED"));
    }
}
