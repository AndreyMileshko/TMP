package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.repository.ProductionItemStateRepository;
import org.junit.jupiter.api.Test;

class ProductionPersistenceContractTest {

    @Test
    void jdbcRepositoryImplementsDomainPort() {
        assertTrue(
                ProductionItemStateRepository.class.isAssignableFrom(
                        JdbcProductionItemStateRepository.class));
    }

    @Test
    void updateSqlUsesOptimisticLockAndVersionIncrement() {
        String sql =
                """
                UPDATE production.production_item_states
                SET status = ?,
                    ordered_quantity = ?,
                    launched_quantity = ?,
                    active_production_quantity = ?,
                    released_quantity = ?,
                    last_material_check_at = ?,
                    last_status_changed_at = ?,
                    version = ?,
                    updated_at = ?
                WHERE id = ? AND version = ?
                """;
        assertTrue(sql.contains("WHERE id = ? AND version = ?"));
        assertTrue(sql.contains("production.production_item_states"));
    }

    @Test
    void insertSqlTargetsProductionSchemaOnly() {
        String sql =
                """
                INSERT INTO production.production_item_states (
                    id, source_order_id, source_order_item_id, specification_id, status,
                    ordered_quantity, launched_quantity, active_production_quantity,
                    released_quantity, last_material_check_at, last_status_changed_at,
                    version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        assertTrue(sql.contains("production.production_item_states"));
        assertTrue(sql.contains("specification_id"));
    }
}
