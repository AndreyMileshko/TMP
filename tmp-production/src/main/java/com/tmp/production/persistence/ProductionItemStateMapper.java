package com.tmp.production.persistence;

import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Maps between {@link ProductionItemState} and {@link ProductionItemStateEntity}. */
public final class ProductionItemStateMapper {

    private ProductionItemStateMapper() {}

    public static ProductionItemStateEntity toEntity(ProductionItemState state) {
        Objects.requireNonNull(state, "state");
        ProductionItemStateEntity entity = new ProductionItemStateEntity();
        entity.setSourceOrderId(state.sourceOrderId());
        entity.setSourceOrderItemId(state.sourceOrderItemId());
        entity.setSpecificationId(state.specificationId());
        entity.setStatus(state.status());
        entity.setOrderedQuantity(state.orderedQuantity());
        entity.setLaunchedQuantity(state.launchedQuantity());
        entity.setActiveProductionQuantity(state.activeProductionQuantity());
        entity.setReleasedQuantity(state.releasedQuantity());
        entity.setLastMaterialCheckAt(state.lastMaterialCheckAt());
        entity.setLastStatusChangedAt(state.lastStatusChangedAt());
        entity.setCreatedAt(state.foundation().frozenAt());
        entity.setCuttingPlanLinks(state.cuttingPlanLinks());
        return entity;
    }

    public static ProductionItemState toDomain(ProductionItemStateEntity entity) {
        Objects.requireNonNull(entity, "entity");
        ProductionFoundation foundation =
                ProductionFoundation.restore(
                        entity.sourceOrderId(),
                        entity.sourceOrderItemId(),
                        entity.specificationId(),
                        entity.createdAt());
        CuttingPlanLinks links =
                entity.cuttingPlanLinks() == null
                        ? CuttingPlanLinks.empty()
                        : entity.cuttingPlanLinks();
        return ProductionItemState.rehydrate(
                foundation,
                entity.status(),
                entity.orderedQuantity(),
                entity.launchedQuantity(),
                entity.activeProductionQuantity(),
                entity.releasedQuantity(),
                entity.lastMaterialCheckAt(),
                entity.lastStatusChangedAt(),
                links);
    }

    public static ProductionItemStateEntity mapRow(ResultSet rs) throws SQLException {
        ProductionItemStateEntity entity = new ProductionItemStateEntity();
        entity.setId(ProductionItemId.of(rs.getObject("id", UUID.class)));
        entity.setSourceOrderId(SourceOrderId.of(rs.getObject("source_order_id", UUID.class)));
        entity.setSourceOrderItemId(
                SourceOrderItemId.of(rs.getObject("source_order_item_id", UUID.class)));
        entity.setSpecificationId(SpecificationId.of(rs.getObject("specification_id", UUID.class)));
        entity.setStatus(ProductionStatus.valueOf(rs.getString("status")));
        entity.setOrderedQuantity(quantityFromRow(rs.getLong("ordered_quantity"), true));
        entity.setLaunchedQuantity(quantityFromRow(rs.getLong("launched_quantity"), false));
        entity.setActiveProductionQuantity(
                quantityFromRow(rs.getLong("active_production_quantity"), false));
        entity.setReleasedQuantity(quantityFromRow(rs.getLong("released_quantity"), false));
        Timestamp materialCheck = rs.getTimestamp("last_material_check_at");
        entity.setLastMaterialCheckAt(materialCheck == null ? null : materialCheck.toInstant());
        entity.setLastStatusChangedAt(
                rs.getTimestamp("last_status_changed_at").toInstant());
        entity.setVersion(rs.getLong("version"));
        entity.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        entity.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        entity.setCuttingPlanLinks(CuttingPlanLinks.empty());
        return entity;
    }

    private static ProductionQuantity quantityFromRow(long value, boolean ordered) {
        return ordered ? ProductionQuantity.positive(value) : ProductionQuantity.nonNegative(value);
    }
}
