package com.tmp.production.application;

import com.tmp.production.domain.ProductionMaterialTransferId;
import java.util.Objects;

/**
 * User command «Подтвердить получение» for one Production logical material transfer.
 *
 * <p>Does not accept Warehouse draft/operation IDs, material, quantity or cell identifiers from UI —
 * those are loaded exclusively from the persisted {@code ProductionMaterialTransfer}.
 */
public record ConfirmMaterialReceiptCommand(ProductionMaterialTransferId logicalTransferId) {

    public ConfirmMaterialReceiptCommand {
        Objects.requireNonNull(logicalTransferId, "logicalTransferId");
    }
}
