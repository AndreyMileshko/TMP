package com.tmp.production.domain.repository;

import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import java.util.List;
import java.util.Optional;

/** Persistence port for Production-owned logical material transfers. */
public interface ProductionMaterialTransferRepository {

    ProductionMaterialTransfer save(ProductionMaterialTransfer transfer);

    Optional<ProductionMaterialTransfer> findById(ProductionMaterialTransferId logicalTransferId);

    Optional<ProductionMaterialTransfer> findByTemplateId(MaterialTransferTemplateId templateId);

    List<ProductionMaterialTransfer> findBySourceOrderId(SourceOrderId sourceOrderId);
}
