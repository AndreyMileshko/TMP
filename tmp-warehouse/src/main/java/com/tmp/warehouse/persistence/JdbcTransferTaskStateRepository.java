package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.TransferTaskAssignment;
import com.tmp.warehouse.domain.repository.TransferTaskStateRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Clock;
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

/** JDBC adapter for {@code warehouse.transfer_task_state}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock.")
public final class JdbcTransferTaskStateRepository implements TransferTaskStateRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcTransferTaskStateRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<TransferTaskAssignment> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        try {
            TransferTaskAssignment row =
                    jdbc.queryForObject(
                            """
                            SELECT document_id, working_user_id, working_since, updated_at
                              FROM warehouse.transfer_task_state
                             WHERE document_id = ?
                            """,
                            (rs, rowNum) ->
                                    TransferTaskAssignment.of(
                                            (UUID) rs.getObject("document_id"),
                                            (UUID) rs.getObject("working_user_id"),
                                            rs.getTimestamp("working_since").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant()),
                            documentId);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, TransferTaskAssignment> findByDocumentIds(Collection<UUID> documentIds) {
        Objects.requireNonNull(documentIds, "documentIds");
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(documentIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT document_id, working_user_id, working_since, updated_at
                  FROM warehouse.transfer_task_state
                 WHERE document_id IN (%s)
                """
                        .formatted(placeholders);
        Map<UUID, TransferTaskAssignment> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    TransferTaskAssignment assignment =
                            TransferTaskAssignment.of(
                                    (UUID) rs.getObject("document_id"),
                                    (UUID) rs.getObject("working_user_id"),
                                    rs.getTimestamp("working_since").toInstant(),
                                    rs.getTimestamp("updated_at").toInstant());
                    result.put(assignment.documentId(), assignment);
                },
                ids.toArray());
        return Map.copyOf(result);
    }

    @Override
    public TransferTaskAssignment takeInWork(
            UUID documentId, UUID workingUserId, Instant workingSince) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(workingUserId, "workingUserId");
        Objects.requireNonNull(workingSince, "workingSince");
        Instant updatedAt = clock.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_task_state (
                    document_id, working_user_id, working_since, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (document_id) DO UPDATE SET
                    working_user_id = EXCLUDED.working_user_id,
                    working_since = EXCLUDED.working_since,
                    updated_at = EXCLUDED.updated_at
                """,
                documentId,
                workingUserId,
                Timestamp.from(workingSince),
                Timestamp.from(updatedAt));
        return TransferTaskAssignment.of(documentId, workingUserId, workingSince, updatedAt);
    }

    @Override
    public void clear(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        jdbc.update(
                "DELETE FROM warehouse.transfer_task_state WHERE document_id = ?", documentId);
    }
}
