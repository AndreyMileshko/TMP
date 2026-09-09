package com.tmp.production.application;

import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.RoutingSnapshotRow;
import java.util.List;
import java.util.Objects;

/**
 * Result of {@link SubmitMaterialRequirementService#submit}. {@code created} is {@code true} for a
 * fresh Submit and {@code false} for an idempotent retry of an already SUBMITTED requirement.
 */
public record SubmitMaterialRequirementResult(
        MaterialRequirement requirement,
        List<GeneratedDocumentLink> documents,
        List<RoutingSnapshotRow> routing,
        boolean created) {

    public SubmitMaterialRequirementResult {
        Objects.requireNonNull(requirement, "requirement");
        documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
        routing = List.copyOf(Objects.requireNonNull(routing, "routing"));
    }
}
