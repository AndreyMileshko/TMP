package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionUiErrorMapperTest {

    @Test
    void mapsExactOrderNotFound() {
        assertEquals(
                ProductionUiErrorMapper.ORDER_NOT_FOUND,
                ProductionUiErrorMapper.text(new IllegalArgumentException("Order not found")));
    }

    @Test
    void doesNotMapDestinationWarehouseNotFoundAsOrderNotFound() {
        String mapped =
                ProductionUiErrorMapper.text(
                        new IllegalStateException(
                                "Configured destination warehouse not found: "
                                        + UUID.randomUUID()));
        assertEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, mapped);
        assertNotEquals(ProductionUiErrorMapper.ORDER_NOT_FOUND, mapped);
    }

    @Test
    void mapsGenericIllegalStateAsTechnicalFailure() {
        String mapped =
                ProductionUiErrorMapper.text(
                        new IllegalStateException(
                                "Не назначен склад производства. Укажите его в Настройки склада → Склады."));
        assertEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, mapped);
        assertNotEquals(ProductionUiErrorMapper.ORDER_NOT_FOUND, mapped);
    }

    @Test
    void mapsQuantityModeOptimisticLockConflict() {
        RuntimeException error =
                new ProductionWorkbenchUiTestSupport.OrderQuantityModeOptimisticLockStubException(
                        UUID.randomUUID(), 0L);
        assertEquals(ProductionUiErrorMapper.QUANTITY_MODE_CONFLICT, ProductionUiErrorMapper.text(error));
        assertTrue(ProductionUiErrorMapper.isQuantityModeConflict(error));
    }

    @Test
    void mapsAcceptConflict() {
        RuntimeException error = new ProductionLaunchConflictStubException();
        assertEquals(ProductionUiErrorMapper.ACCEPT_CONFLICT, ProductionUiErrorMapper.text(error));
        assertTrue(ProductionUiErrorMapper.isAcceptConflict(error));
    }

    @Test
    void exposesCardLoadFailedConstantForOrderCardOperations() {
        assertEquals(
                "Не удалось загрузить данные заказа. Повторите попытку.",
                ProductionUiErrorMapper.CARD_LOAD_FAILED);
    }

    @Test
    void mapsMaterialRequirementUnresolvedAndAmbiguous() {
        RuntimeException unresolved =
                new MaterialRequirementNotReadyStubException(
                        "Material requirement is not ready: UNRESOLVED material for order x,"
                                + " identity=SpecificationMaterialIdentity[materialCode=MAT-001,"
                                + " color=White, unitOfMeasure=шт]");
        String unresolvedText = ProductionUiErrorMapper.text(unresolved);
        assertTrue(unresolvedText.startsWith(ProductionUiErrorMapper.MATERIALS_UNRESOLVED));
        assertTrue(unresolvedText.contains("MAT-001"));
        assertTrue(unresolvedText.contains("White"));
        assertTrue(unresolvedText.contains("шт"));
        assertFalse(unresolvedText.contains("справочник"));
        assertNotEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, unresolvedText);

        RuntimeException ambiguous =
                new MaterialRequirementNotReadyStubException(
                        "Material requirement is not ready: AMBIGUOUS material for order x,"
                                + " identity=SpecificationMaterialIdentity[materialCode=MAT-001,"
                                + " color=White, unitOfMeasure=шт.]");
        String ambiguousText = ProductionUiErrorMapper.text(ambiguous);
        assertTrue(ambiguousText.startsWith(ProductionUiErrorMapper.MATERIALS_AMBIGUOUS));
        assertTrue(ambiguousText.contains("MAT-001"));
        assertNotEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, ambiguousText);
    }

    @Test
    void keepsMaterialShortageMessageForZeroStockSubmit() {
        assertEquals(
                ProductionUiErrorMapper.MATERIAL_SHORTAGE,
                ProductionUiErrorMapper.text(
                        new MaterialRequirementShortageStubException(
                                "MaterialRequirementShortage: no positive AVAILABLE source")));
        assertTrue(ProductionUiErrorMapper.MATERIAL_SHORTAGE.contains("Черновик сохранён"));
        assertFalse(ProductionUiErrorMapper.MATERIAL_SHORTAGE.contains("отправлен на склад"));
    }

    private static final class ProductionLaunchConflictStubException extends RuntimeException {
        ProductionLaunchConflictStubException() {
            super("ProductionLaunchConflict: already launched");
        }
    }

    private static final class MaterialRequirementNotReadyStubException extends RuntimeException {
        MaterialRequirementNotReadyStubException(String message) {
            super(message);
        }
    }

    private static final class MaterialRequirementShortageStubException extends RuntimeException {
        MaterialRequirementShortageStubException(String message) {
            super(message);
        }
    }
}
