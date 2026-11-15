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
class StockAdjustmentRepository {
    private static final String HISTORY = """
            SELECT a.*, u.username AS actor_username
            FROM tradesphere.stock_adjustments a
            JOIN tradesphere.users u ON u.organization_id = a.organization_id AND u.id = a.actor_id
            WHERE a.organization_id = :organizationId
            """;
    private static final String PAIR = """
            organization_id = :organizationId AND warehouse_id = :warehouseId AND product_id = :productId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    StockAdjustmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<StockAdjustment> findByRequest(UUID organizationId, UUID requestId) {
        var parameters = new MapSqlParameterSource("organizationId", organizationId).addValue("requestId", requestId);
        return jdbc.query(HISTORY + " AND a.request_id = :requestId", parameters, StockAdjustmentRepository::adjustment)
                .stream().findFirst();
    }

    Optional<LockedBalance> lockBalance(UUID organizationId, UUID warehouseId, UUID productId) {
        return jdbc.query("SELECT on_hand, reserved FROM tradesphere.inventory_balances WHERE " + PAIR + " FOR NO KEY UPDATE",
                pair(organizationId, warehouseId, productId),
                (rs, row) -> new LockedBalance(rs.getLong("on_hand"), rs.getLong("reserved"))).stream().findFirst();
    }

    boolean insert(UUID organizationId, UUID actorId, StockAdjustmentRequest request, LockedBalance balance) {
        var parameters = pair(organizationId, request.warehouseId(), request.productId())
                .addValue("id", UUID.randomUUID()).addValue("requestId", request.requestId()).addValue("actorId", actorId)
                .addValue("previousOnHand", balance.onHand()).addValue("reserved", balance.reserved())
                .addValue("countedOnHand", request.countedOnHand()).addValue("reason", request.reason());
        return jdbc.update("""
                INSERT INTO tradesphere.stock_adjustments
                    (id, organization_id, request_id, warehouse_id, product_id, previous_on_hand, counted_on_hand,
                     reserved, reason, actor_id)
                VALUES (:id, :organizationId, :requestId, :warehouseId, :productId, :previousOnHand, :countedOnHand,
                        :reserved, :reason, :actorId)
                ON CONFLICT (organization_id, request_id) DO NOTHING
                """, parameters) == 1;
    }

    void updateOnHand(UUID organizationId, StockAdjustmentRequest request) {
        var parameters = pair(organizationId, request.warehouseId(), request.productId())
                .addValue("countedOnHand", request.countedOnHand());
        int changed = jdbc.update("UPDATE tradesphere.inventory_balances SET on_hand = :countedOnHand, updated_at = clock_timestamp() WHERE " + PAIR,
                parameters);
        if (changed != 1) {
            throw new IllegalStateException("The locked stock adjustment balance was not updated.");
        }
    }

    List<StockAdjustment> history(UUID organizationId, UUID warehouseId, UUID productId, int page, int size) {
        var parameters = pair(organizationId, warehouseId, productId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        return jdbc.query(HISTORY + """
                 AND a.warehouse_id = :warehouseId AND a.product_id = :productId
                 ORDER BY a.occurred_at DESC, a.id DESC LIMIT :limit OFFSET :offset
                """, parameters, StockAdjustmentRepository::adjustment);
    }

    long count(UUID organizationId, UUID warehouseId, UUID productId) {
        return jdbc.queryForObject("SELECT count(*) FROM tradesphere.stock_adjustments WHERE " + PAIR,
                pair(organizationId, warehouseId, productId), Long.class);
    }

    private static MapSqlParameterSource pair(UUID organizationId, UUID warehouseId, UUID productId) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("warehouseId", warehouseId)
                .addValue("productId", productId);
    }

    private static StockAdjustment adjustment(ResultSet rs, int row) throws SQLException {
        return new StockAdjustment(rs.getObject("id", UUID.class), rs.getObject("warehouse_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getLong("previous_on_hand"), rs.getLong("counted_on_hand"),
                rs.getLong("reserved"), rs.getString("reason"), rs.getString("actor_username"),
                rs.getTimestamp("occurred_at").toInstant());
    }

    record LockedBalance(long onHand, long reserved) { }
}
