package com.tmp.production.domain;

/** Raised when a Production logical material transfer identity does not exist. */
public final class ProductionMaterialTransferNotFoundException extends RuntimeException {

    public ProductionMaterialTransferNotFoundException(ProductionMaterialTransferId logicalTransferId) {
        super("Production material transfer not found: " + logicalTransferId);
    }
}
