package com.tmp.production.application;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.production.application.document.ProductionReleaseProcessor;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseImmutableException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal Production Release document gateway for STAGE7-013 orchestration.
 *
 * <p>Not a standalone user-facing «Выпустить изделия» use case. Does not call Warehouse.
 * Joins ambient {@code REQUIRED} transactions (ADR-036); does not use {@code REQUIRES_NEW}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators injected by the container.")
public final class ProductionReleaseDocumentService {

    private final DocumentEngine documentEngine;
    private final ProductionReleaseRepository releaseRepository;
    private final Clock clock;

    public ProductionReleaseDocumentService(
            DocumentEngine documentEngine,
            ProductionReleaseRepository releaseRepository,
            Clock clock) {
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
        this.releaseRepository = Objects.requireNonNull(releaseRepository, "releaseRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates a Document Engine DRAFT and persists Production-owned release payload.
     */
    @Transactional
    public DocumentMetadata createDraft(ProductionReleaseDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        Instant releasedAt =
                command.releasedAt() != null ? command.releasedAt() : clock.instant();
        DocumentMetadata draft =
                documentEngine.createDocument(
                        new CreateDocumentCommand(
                                ProductionReleaseProcessor.DOCUMENT_TYPE_ID,
                                "Production Release: " + command.sourceOrderId()));
        ProductionRelease release =
                ProductionRelease.draft(
                        draft.id(),
                        SourceOrderId.of(command.sourceOrderId()),
                        releasedAt,
                        mapItemLines(command.itemLines()),
                        mapMaterialLines(command.materialLines()));
        releaseRepository.saveDraft(release);
        return draft;
    }

    /**
     * Replaces DRAFT payload content. Rejected after POST.
     */
    @Transactional
    public ProductionRelease updateDraft(
            UUID documentId, ProductionReleaseDocumentCommand command) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(command, "command");
        ProductionRelease existing =
                releaseRepository
                        .findByDocumentId(documentId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Production Release not found: " + documentId));
        if (existing.posted()) {
            throw new ProductionReleaseImmutableException(documentId);
        }
        Instant releasedAt =
                command.releasedAt() != null ? command.releasedAt() : existing.releasedAt();
        ProductionRelease updated =
                existing.replaceDraftContent(
                        releasedAt,
                        mapItemLines(command.itemLines()),
                        mapMaterialLines(command.materialLines()));
        return releaseRepository.saveDraft(updated);
    }

    /**
     * Posts the Production Release document via Document Engine (joins ambient TX).
     */
    @Transactional
    public DocumentMetadata post(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return documentEngine.postDocument(documentId);
    }

    /**
     * Durable release read model for tests / future history.
     */
    @Transactional(readOnly = true)
    public Optional<ProductionRelease> findReleaseByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return releaseRepository.findByDocumentId(documentId);
    }

    /**
     * Actual material usage projection for STAGE7-013 Warehouse Consumption aggregation.
     */
    @Transactional(readOnly = true)
    public List<ProductionRelease.ActualMaterialUsage> actualMaterialUsages(UUID documentId) {
        return findReleaseByDocumentId(documentId)
                .map(ProductionRelease::actualMaterialUsages)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Production Release not found: " + documentId));
    }

    private static List<ProductionRelease.ItemLine> mapItemLines(
            List<ProductionReleaseDocumentCommand.ItemLine> lines) {
        return lines.stream()
                .map(
                        line ->
                                new ProductionRelease.ItemLine(
                                        SourceOrderItemId.of(line.sourceOrderItemId()),
                                        SpecificationId.of(line.specificationId()),
                                        ProductionQuantity.positive(line.releaseQuantity())))
                .toList();
    }

    private static List<ProductionRelease.MaterialLine> mapMaterialLines(
            List<ProductionReleaseDocumentCommand.MaterialLine> lines) {
        return lines.stream()
                .map(
                        line ->
                                new ProductionRelease.MaterialLine(
                                        MaterialReferenceId.of(line.materialReferenceId()),
                                        line.plannedQuantity(),
                                        line.actualQuantity(),
                                        line.planningSource(),
                                        Optional.ofNullable(line.cuttingPlanId())
                                                .map(CuttingPlanId::of),
                                        Optional.ofNullable(line.sourceOrderItemId())
                                                .map(SourceOrderItemId::of),
                                        Optional.ofNullable(line.comment())))
                .toList();
    }

    /**
     * Internal command for preparing a Production Release document (not UI orchestration).
     */
    public record ProductionReleaseDocumentCommand(
            UUID sourceOrderId,
            Instant releasedAt,
            List<ItemLine> itemLines,
            List<MaterialLine> materialLines) {

        public ProductionReleaseDocumentCommand {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(itemLines, "itemLines");
            Objects.requireNonNull(materialLines, "materialLines");
            itemLines = List.copyOf(itemLines);
            materialLines = List.copyOf(materialLines);
        }

        public record ItemLine(
                UUID sourceOrderItemId, UUID specificationId, BigDecimal releaseQuantity) {

            public ItemLine {
                Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
                Objects.requireNonNull(specificationId, "specificationId");
                Objects.requireNonNull(releaseQuantity, "releaseQuantity");
            }
        }

        public record MaterialLine(
                UUID materialReferenceId,
                BigDecimal plannedQuantity,
                BigDecimal actualQuantity,
                MaterialPlanningSource planningSource,
                UUID cuttingPlanId,
                UUID sourceOrderItemId,
                String comment) {

            public MaterialLine {
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
                Objects.requireNonNull(plannedQuantity, "plannedQuantity");
                Objects.requireNonNull(actualQuantity, "actualQuantity");
                Objects.requireNonNull(planningSource, "planningSource");
            }
        }
    }
}
