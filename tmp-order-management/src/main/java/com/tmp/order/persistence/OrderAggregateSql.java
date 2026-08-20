package com.tmp.order.persistence;

/**
 * SQL contract for Order Management aggregate tables (Specification §19). Adapters participate in
 * the caller's transaction and do not open an independent one.
 */
final class OrderAggregateSql {

    static final String INSERT_ORDER =
            """
            INSERT INTO order_management.orders
              (order_id, order_number, customer_ref, customer_name, contract_ref, site_ref,
               responsible_manager, direction, currency, status, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    static final String UPDATE_ORDER =
            """
            UPDATE order_management.orders
            SET customer_ref = ?, customer_name = ?, contract_ref = ?, site_ref = ?,
                responsible_manager = ?, direction = ?, currency = ?, status = ?,
                version = version + 1, updated_at = ?
            WHERE order_id = ? AND version = ?
            """;

    static final String SELECT_ORDER_BY_ID =
            """
            SELECT order_id, order_number, customer_ref, customer_name, contract_ref, site_ref,
                   responsible_manager, direction, currency, status, version, created_at, updated_at
            FROM order_management.orders
            WHERE order_id = ?
            """;

    static final String EXISTS_ORDER_BY_NUMBER =
            """
            SELECT COUNT(*) FROM order_management.orders WHERE order_number = ?
            """;

    static final String INSERT_ORDER_ITEM =
            """
            INSERT INTO order_management.order_items
              (order_item_id, order_id, product_code, item_name, comments, external_position_number,
               status, active_revision_number, draft_revision_number, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    static final String UPDATE_ORDER_ITEM =
            """
            UPDATE order_management.order_items
            SET product_code = ?, item_name = ?, comments = ?, external_position_number = ?,
                status = ?, active_revision_number = ?, draft_revision_number = ?,
                version = version + 1, updated_at = ?
            WHERE order_item_id = ? AND version = ?
            """;

    static final String SELECT_ORDER_ITEM_BY_ID =
            """
            SELECT order_item_id, order_id, product_code, item_name, comments,
                   external_position_number, status, active_revision_number, draft_revision_number,
                   version, created_at, updated_at
            FROM order_management.order_items
            WHERE order_item_id = ?
            """;

    static final String SELECT_ORDER_ITEMS_BY_ORDER_ID =
            """
            SELECT order_item_id, order_id, product_code, item_name, comments,
                   external_position_number, status, active_revision_number, draft_revision_number,
                   version, created_at, updated_at
            FROM order_management.order_items
            WHERE order_id = ?
            ORDER BY created_at ASC, order_item_id ASC
            """;

    static final String DELETE_SPEC_LINES_FOR_ITEM =
            """
            DELETE FROM order_management.item_specification_lines WHERE order_item_id = ?
            """;

    static final String DELETE_SPECS_FOR_ITEM =
            """
            DELETE FROM order_management.item_specifications WHERE order_item_id = ?
            """;

    static final String DELETE_REVISIONS_FOR_ITEM =
            """
            DELETE FROM order_management.order_item_revisions WHERE order_item_id = ?
            """;

    static final String INSERT_REVISION =
            """
            INSERT INTO order_management.order_item_revisions
              (order_item_id, revision_number, revision_status, ordered_quantity,
               previous_revision_number)
            VALUES (?, ?, ?, ?, ?)
            """;

    static final String INSERT_SPECIFICATION =
            """
            INSERT INTO order_management.item_specifications
              (order_item_id, revision_number, immutable, specification_id)
            VALUES (?, ?, ?, ?)
            """;

    static final String INSERT_SPEC_LINE =
            """
            INSERT INTO order_management.item_specification_lines
              (order_item_id, revision_number, line_number, material_code, material_name, color,
               length_mm, line_quantity, unit_of_measure)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    static final String SELECT_REVISIONS_BY_ITEM =
            """
            SELECT order_item_id, revision_number, revision_status, ordered_quantity,
                   previous_revision_number
            FROM order_management.order_item_revisions
            WHERE order_item_id = ?
            ORDER BY revision_number ASC
            """;

    static final String SELECT_REVISION_BY_KEY =
            """
            SELECT order_item_id, revision_number, revision_status, ordered_quantity,
                   previous_revision_number
            FROM order_management.order_item_revisions
            WHERE order_item_id = ? AND revision_number = ?
            """;

    static final String SELECT_SPEC_BY_KEY =
            """
            SELECT order_item_id, revision_number, immutable
            FROM order_management.item_specifications
            WHERE order_item_id = ? AND revision_number = ?
            """;

    static final String SELECT_SPEC_LINES_BY_KEY =
            """
            SELECT line_number, material_code, material_name, color, length_mm, line_quantity,
                   unit_of_measure
            FROM order_management.item_specification_lines
            WHERE order_item_id = ? AND revision_number = ?
            ORDER BY line_number ASC
            """;

    private OrderAggregateSql() {}
}
