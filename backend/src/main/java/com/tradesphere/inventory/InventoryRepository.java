package com.tradesphere.inventory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
    // Read-model joins use composite organization keys; they do not mutate other capabilities' tables.
    private static final String BALANCES = """
            FROM tradesphere.inventory_balances b
            JOIN tradesphere.warehouses w ON w.organization_id = b.organization_id AND w.id = b.warehouse_id
            JOIN tradesphere.products p ON p.organization_id = b.organization_id AND p.id = b.product_id
            WHERE b.organization_id = :organizationId
              AND (CAST(:warehouseId AS uuid) IS NULL OR b.warehouse_id = :warehouseId)
              AND (CAST(:productId AS uuid) IS NULL OR b.product_id = :productId)
              AND (:q = '' OR p.name ILIKE :pattern ESCAPE '!' OR p.sku ILIKE :pattern ESCAPE '!'
                   OR w.name ILIKE :pattern ESCAPE '!' OR w.code ILIKE :pattern ESCAPE '!')
            """;
    private static final String MOVEMENTS = """
            FROM tradesphere.stock_movements m
            JOIN tradesphere.warehouses w ON w.organization_id = m.organization_id AND w.id = m.warehouse_id
            JOIN tradesphere.products p ON p.organization_id = m.organization_id AND p.id = m.product_id
            JOIN tradesphere.users u ON u.organization_id = m.organization_id AND u.id = m.received_by
            WHERE m.organization_id = :organizationId
            """;
    private static final String MOVEMENT_COLUMNS = """
            SELECT m.*, w.code AS warehouse_code, w.name AS warehouse_name,
                   p.sku, p.name AS product_name, p.unit_of_measure, u.username AS received_by_username
            """;
    private static final String MOVEMENT_FILTER = """
              AND (CAST(:warehouseId AS uuid) IS NULL OR m.warehouse_id = :warehouseId)
              AND (CAST(:productId AS uuid) IS NULL OR m.product_id = :productId)
              AND (:q = '' OR p.name ILIKE :pattern ESCAPE '!' OR p.sku ILIKE :pattern ESCAPE '!'
                   OR w.code ILIKE :pattern ESCAPE '!' OR m.reference ILIKE :pattern ESCAPE '!')
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public InventoryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<StockMovement> receiptByRequest(UUID organizationId, UUID requestId) {
        return jdbc.query(MOVEMENT_COLUMNS + MOVEMENTS + " AND m.request_id = :requestId",
                new MapSqlParameterSource("organizationId", organizationId).addValue("requestId", requestId), InventoryRepository::movement).stream().findFirst();
    }

    public Optional<StockMovement> movement(UUID organizationId, UUID id) {
        return jdbc.query(MOVEMENT_COLUMNS + MOVEMENTS + " AND m.id = :id",
                new MapSqlParameterSource("organizationId", organizationId).addValue("id", id), InventoryRepository::movement).stream().findFirst();
    }

    public Optional<UUID> insertReceipt(UUID organizationId, UUID actorId, StockReceiptRequest request) {
        var parameters = new MapSqlParameterSource("id", UUID.randomUUID()).addValue("organizationId", organizationId)
                .addValue("requestId", request.requestId()).addValue("warehouseId", request.warehouseId())
                .addValue("productId", request.productId()).addValue("quantity", request.quantity())
                .addValue("reference", request.reference()).addValue("note", request.note()).addValue("actorId", actorId);
        return jdbc.query("""
                INSERT INTO tradesphere.stock_movements (id, organization_id, request_id, warehouse_id, product_id, quantity, reference, note, received_by)
                VALUES (:id, :organizationId, :requestId, :warehouseId, :productId, :quantity, :reference, :note, :actorId)
                ON CONFLICT (organization_id, request_id) DO NOTHING RETURNING id
                """, parameters, (rs, row) -> rs.getObject("id", UUID.class)).stream().findFirst();
    }

    public boolean increaseBalance(UUID organizationId, StockReceiptRequest request) {
        return !jdbc.query("""
                INSERT INTO tradesphere.inventory_balances (organization_id, warehouse_id, product_id, on_hand, reserved)
                VALUES (:organizationId, :warehouseId, :productId, :quantity, 0)
                ON CONFLICT (organization_id, warehouse_id, product_id) DO UPDATE
                SET on_hand = tradesphere.inventory_balances.on_hand + EXCLUDED.on_hand, updated_at = now()
                WHERE tradesphere.inventory_balances.on_hand <= :maximum - EXCLUDED.on_hand
                RETURNING on_hand
                """, new MapSqlParameterSource("organizationId", organizationId).addValue("warehouseId", request.warehouseId())
                .addValue("productId", request.productId()).addValue("quantity", request.quantity())
                .addValue("maximum", StockReceiptRequest.MAX_QUANTITY), (rs, row) -> rs.getLong("on_hand")).isEmpty();
    }

    public List<InventoryBalance> balances(UUID organizationId, String q, UUID warehouseId, UUID productId, int page, int size) {
        return jdbc.query("""
                SELECT b.*, w.code AS warehouse_code, w.name AS warehouse_name, w.active AS warehouse_active,
                       p.sku, p.name AS product_name, p.unit_of_measure, p.active AS product_active
                """ + BALANCES + " ORDER BY lower(p.name), lower(w.name), b.product_id, b.warehouse_id LIMIT :limit OFFSET :offset",
                filter(organizationId, q, warehouseId, productId).addValue("limit", size).addValue("offset", (long) page * size),
                (rs, row) -> new InventoryBalance(rs.getObject("warehouse_id", UUID.class), rs.getString("warehouse_code"), rs.getString("warehouse_name"),
                        rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("product_name"), rs.getString("unit_of_measure"),
                        rs.getLong("on_hand"), rs.getLong("reserved"), rs.getLong("available"), rs.getBoolean("warehouse_active"),
                        rs.getBoolean("product_active"), rs.getTimestamp("updated_at").toInstant()));
    }

    public long balanceCount(UUID organizationId, String q, UUID warehouseId, UUID productId) {
        return jdbc.queryForObject("SELECT count(*) " + BALANCES, filter(organizationId, q, warehouseId, productId), Long.class);
    }

    public List<StockMovement> movements(UUID organizationId, String q, UUID warehouseId, UUID productId, int page, int size) {
        return jdbc.query(MOVEMENT_COLUMNS + MOVEMENTS + MOVEMENT_FILTER + " ORDER BY m.received_at DESC, m.id DESC LIMIT :limit OFFSET :offset",
                filter(organizationId, q, warehouseId, productId).addValue("limit", size).addValue("offset", (long) page * size), InventoryRepository::movement);
    }

    public long movementCount(UUID organizationId, String q, UUID warehouseId, UUID productId) {
        return jdbc.queryForObject("SELECT count(*) " + MOVEMENTS + MOVEMENT_FILTER, filter(organizationId, q, warehouseId, productId), Long.class);
    }

    private static MapSqlParameterSource filter(UUID organizationId, String q, UUID warehouseId, UUID productId) {
        var pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new MapSqlParameterSource("organizationId", organizationId).addValue("q", q).addValue("pattern", pattern)
                .addValue("warehouseId", warehouseId, java.sql.Types.OTHER).addValue("productId", productId, java.sql.Types.OTHER);
    }

    private static StockMovement movement(ResultSet rs, int row) throws SQLException {
        return new StockMovement(rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class), rs.getString("warehouse_code"), rs.getString("warehouse_name"),
                rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("product_name"), rs.getString("unit_of_measure"),
                rs.getLong("quantity"), rs.getString("reference"), rs.getString("note"), rs.getObject("received_by", UUID.class),
                rs.getString("received_by_username"), rs.getTimestamp("received_at").toInstant());
    }
}
