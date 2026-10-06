package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.DemandTaskAssignment;
import com.tmp.warehouse.domain.repository.DemandTaskStateRepository;
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

/** JDBC adapter for {@code warehouse.demand_task_state}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock.")
public final class JdbcDemandTaskStateRepository implements DemandTaskStateRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcDemandTaskStateRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<DemandTaskAssignment> findByDemandId(UUID demandId) {
        Objects.requireNonNull(demandId, "demandId");
        try {
            DemandTaskAssignment row =
                    jdbc.queryForObject(
                            """
                            SELECT demand_id, working_user_id, working_since, updated_at
                              FROM warehouse.demand_task_state
                             WHERE demand_id = ?
                            """,
                            (rs, rowNum) ->
                                    DemandTaskAssignment.of(
                                            (UUID) rs.getObject("demand_id"),
                                            (UUID) rs.getObject("working_user_id"),
                                            rs.getTimestamp("working_since").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant()),
                            demandId);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, DemandTaskAssignment> findByDemandIds(Collection<UUID> demandIds) {
        Objects.requireNonNull(demandIds, "demandIds");
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(demandIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                ("SELECT demand_id, working_user_id, working_since, updated_at "
                                + "FROM warehouse.demand_task_state "
                                + "WHERE demand_id IN (%s)")
                        .formatted(placeholders);
        Map<UUID, DemandTaskAssignment> result = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    DemandTaskAssignment assignment =
                            DemandTaskAssignment.of(
                                    (UUID) rs.getObject("demand_id"),
                                    (UUID) rs.getObject("working_user_id"),
                                    rs.getTimestamp("working_since").toInstant(),
                                    rs.getTimestamp("updated_at").toInstant());
                    result.put(assignment.demandId(), assignment);
                },
                ids.toArray());
        return Map.copyOf(result);
    }

    @Override
    public DemandTaskAssignment takeInWork(
            UUID demandId, UUID workingUserId, Instant workingSince) {
        Objects.requireNonNull(demandId, "demandId");
        Objects.requireNonNull(workingUserId, "workingUserId");
        Objects.requireNonNull(workingSince, "workingSince");
        Instant updatedAt = clock.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.demand_task_state (
                    demand_id, working_user_id, working_since, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (demand_id) DO UPDATE SET
                    working_user_id = EXCLUDED.working_user_id,
                    working_since = EXCLUDED.working_since,
                    updated_at = EXCLUDED.updated_at
                """,
                demandId,
                workingUserId,
                Timestamp.from(workingSince),
                Timestamp.from(updatedAt));
        return DemandTaskAssignment.of(demandId, workingUserId, workingSince, updatedAt);
    }

    @Override
    public void clear(UUID demandId) {
        Objects.requireNonNull(demandId, "demandId");
        jdbc.update("DELETE FROM warehouse.demand_task_state WHERE demand_id = ?", demandId);
    }
}
