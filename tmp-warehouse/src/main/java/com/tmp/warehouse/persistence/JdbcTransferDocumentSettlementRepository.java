package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferSettlementDecision;
import com.tmp.warehouse.domain.TransferSettlementOptimisticLockException;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC adapter for {@code warehouse.transfer_document_settlement}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate.")
public final class JdbcTransferDocumentSettlementRepository
        implements TransferDocumentSettlementRepository {

    /** Upper bound for a single {@code IN (...)} batch to stay within typical JDBC parameter limits. */
    private static final int BATCH_IN_CHUNK_SIZE = 500;

    private static final String SELECT_COLUMNS =
            """
            SELECT document_id, settlement_state, operational_revision, decision,
                   rejection_reason, rejected_at, rejected_by, created_at, updated_at
              FROM warehouse.transfer_document_settlement
            """;

    private final JdbcTemplate jdbc;

    public JdbcTransferDocumentSettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insertAwaitingReceipt(TransferDocumentSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (settlement.settlementState() != TransferSettlementState.AWAITING_RECEIPT
                || settlement.decision().isPresent()
                || settlement.operationalRevision() != 0L) {
            throw new IllegalArgumentException(
                    "insertAwaitingReceipt requires AWAITING_RECEIPT revision 0 undecided settlement");
        }
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_settlement (
                    document_id, settlement_state, operational_revision, decision,
                    rejection_reason, rejected_at, rejected_by, created_at, updated_at)
                VALUES (?, ?, ?, NULL, NULL, NULL, NULL, ?, ?)
                """,
                settlement.documentId(),
                TransferSettlementState.AWAITING_RECEIPT.name(),
                0L,
                Timestamp.from(settlement.createdAt()),
                Timestamp.from(settlement.updatedAt()));
    }

    @Override
    public Optional<TransferDocumentSettlement> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return queryOne(SELECT_COLUMNS + " WHERE document_id = ?", documentId);
    }

    @Override
    public Map<UUID, TransferDocumentSettlement> findByDocumentIds(Collection<UUID> documentIds) {
        Objects.requireNonNull(documentIds, "documentIds");
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(documentIds);
        Map<UUID, TransferDocumentSettlement> result = new HashMap<>();
        for (int offset = 0; offset < ids.size(); offset += BATCH_IN_CHUNK_SIZE) {
            List<UUID> chunk =
                    ids.subList(offset, Math.min(offset + BATCH_IN_CHUNK_SIZE, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            String sql = SELECT_COLUMNS + " WHERE document_id IN (" + placeholders + ")";
            jdbc.query(
                    sql,
                    rs -> {
                        TransferDocumentSettlement settlement = mapRow(rs);
                        result.put(settlement.documentId(), settlement);
                    },
                    chunk.toArray());
        }
        return Map.copyOf(result);
    }

    @Override
    public Optional<TransferDocumentSettlement> lockByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return queryOne(SELECT_COLUMNS + " WHERE document_id = ? FOR UPDATE", documentId);
    }

    @Override
    public void markAcceptedAndSettled(
            UUID documentId, long expectedOperationalRevision, TransferDocumentSettlement updated) {
        markAcceptedTransition(
                documentId,
                expectedOperationalRevision,
                updated,
                TransferSettlementState.SETTLED,
                "markAcceptedAndSettled");
    }

    @Override
    public void markAcceptedAndReturnPending(
            UUID documentId, long expectedOperationalRevision, TransferDocumentSettlement updated) {
        markAcceptedTransition(
                documentId,
                expectedOperationalRevision,
                updated,
                TransferSettlementState.RETURN_PENDING,
                "markAcceptedAndReturnPending");
    }

    private void markAcceptedTransition(
            UUID documentId,
            long expectedOperationalRevision,
            TransferDocumentSettlement updated,
            TransferSettlementState targetState,
            String methodName) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(updated, "updated");
        if (!documentId.equals(updated.documentId())) {
            throw new IllegalArgumentException("documentId mismatch");
        }
        if (updated.settlementState() != targetState
                || updated.decision().orElse(null) != TransferSettlementDecision.ACCEPTED) {
            throw new IllegalArgumentException(
                    methodName + " requires " + targetState + "/ACCEPTED");
        }
        if (updated.operationalRevision() != expectedOperationalRevision + 1) {
            throw new IllegalArgumentException(
                    "updated revision must be expected + 1: expected="
                            + expectedOperationalRevision
                            + ", updated="
                            + updated.operationalRevision());
        }
        int rows =
                jdbc.update(
                        """
                        UPDATE warehouse.transfer_document_settlement
                           SET settlement_state = ?,
                               decision = ?,
                               operational_revision = ?,
                               updated_at = ?
                         WHERE document_id = ?
                           AND operational_revision = ?
                           AND settlement_state = ?
                           AND decision IS NULL
                        """,
                        targetState.name(),
                        TransferSettlementDecision.ACCEPTED.name(),
                        updated.operationalRevision(),
                        Timestamp.from(updated.updatedAt()),
                        documentId,
                        expectedOperationalRevision,
                        TransferSettlementState.AWAITING_RECEIPT.name());
        if (rows != 1) {
            TransferDocumentSettlement current =
                    findByDocumentId(documentId)
                            .orElseThrow(
                                    () ->
                                            new InvalidWarehouseStateException(
                                                    "Settlement missing after failed accept: "
                                                            + documentId));
            if (current.operationalRevision() != expectedOperationalRevision) {
                throw new TransferSettlementOptimisticLockException(
                        documentId, expectedOperationalRevision, current.operationalRevision());
            }
            throw new InvalidWarehouseStateException(
                    "Cannot mark settlement accepted: documentId="
                            + documentId
                            + ", state="
                            + current.settlementState()
                            + ", decision="
                            + current.decision().orElse(null));
        }
    }

    private Optional<TransferDocumentSettlement> queryOne(String sql, UUID documentId) {
        try {
            return Optional.of(
                    jdbc.queryForObject(sql, (rs, rowNum) -> mapRow(rs), documentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static TransferDocumentSettlement mapRow(ResultSet rs) throws SQLException {
        String decisionRaw = rs.getString("decision");
        Instant rejectedAt =
                rs.getTimestamp("rejected_at") == null
                        ? null
                        : rs.getTimestamp("rejected_at").toInstant();
        return TransferDocumentSettlement.of(
                (UUID) rs.getObject("document_id"),
                TransferSettlementState.parse(rs.getString("settlement_state")),
                rs.getLong("operational_revision"),
                TransferSettlementDecision.parse(decisionRaw),
                rs.getString("rejection_reason"),
                rejectedAt,
                (UUID) rs.getObject("rejected_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
