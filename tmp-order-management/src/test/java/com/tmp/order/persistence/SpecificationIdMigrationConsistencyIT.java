package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * STAGE7-004B: Verifies that the SQL migration path and the Java path produce
 * identical SpecificationId UUIDs for the same (orderItemId, revisionNumber).
 */
@Testcontainers
class SpecificationIdMigrationConsistencyIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    void migrationAndJavaProduceSameSpecificationId() {
        UUID orderItemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        int revisionNumber = 3;

        UUID sqlResult = jdbc.queryForObject(
                "SELECT ( "
                        + "  SELECT ( "
                        + "    substring(h from 1 for 12) || "
                        + "    '3' || substring(h from 14 for 3) || "
                        + "    lpad(to_hex((get_byte(decode(substring(h from 17 for 2), 'hex'), 0) & 63 | 128)), 2, '0') || "
                        + "    substring(h from 19 for 14) "
                        + "  )::uuid "
                        + "  FROM (SELECT md5(?::text || ':' || ?::text) AS h) sub "
                        + ")",
                UUID.class, orderItemId, revisionNumber);

        UUID javaResult = OrderItemAggregateJdbcSupport.deriveSpecificationId(
                OrderItemId.of(orderItemId), RevisionNumber.of(revisionNumber));

        assertEquals(javaResult, sqlResult,
                "SQL migration algorithm must produce the same UUID as Java UUID.nameUUIDFromBytes()");
    }

    @Test
    void migrationThenJavaRederivationProducesSameId() {
        UUID orderItemId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        int revisionNumber = 1;

        UUID migrated = jdbc.queryForObject(
                "SELECT ( "
                        + "  SELECT ( "
                        + "    substring(h from 1 for 12) || "
                        + "    '3' || substring(h from 14 for 3) || "
                        + "    lpad(to_hex((get_byte(decode(substring(h from 17 for 2), 'hex'), 0) & 63 | 128)), 2, '0') || "
                        + "    substring(h from 19 for 14) "
                        + "  )::uuid "
                        + "  FROM (SELECT md5(?::text || ':' || ?::text) AS h) sub "
                        + ")",
                UUID.class, orderItemId, revisionNumber);

        UUID reDerived = OrderItemAggregateJdbcSupport.deriveSpecificationId(
                OrderItemId.of(orderItemId), RevisionNumber.of(revisionNumber));

        assertEquals(migrated, reDerived,
                "After migration, Java re-derivation must produce the same SpecificationId");
    }
}
