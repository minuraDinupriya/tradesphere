package com.tradesphere.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderReservationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public OrderReservationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Reservation> lock(UUID organizationId, UUID orderId) {
        return jdbc.query("""
                SELECT product_id,quantity,status FROM tradesphere.stock_reservations
                WHERE organization_id = :organizationId AND order_id = :orderId ORDER BY product_id FOR UPDATE
                """, scope(organizationId, orderId), (rs, row) -> new Reservation(rs.getObject("product_id", UUID.class), rs.getLong("quantity"), rs.getString("status")));
    }

    public boolean close(UUID organizationId, UUID orderId, UUID warehouseId, UUID actorId, Reservation reservation, boolean consume) {
        var parameters = scope(organizationId, orderId).addValue("productId", reservation.productId()).addValue("warehouseId", warehouseId)
                .addValue("quantity", reservation.quantity()).addValue("consumed", consume ? reservation.quantity() : 0L)
                .addValue("actorId", actorId).addValue("status", consume ? "CONSUMED" : "RELEASED")
                .addValue("kind", consume ? "FULFILL" : "RELEASE").addValue("id", UUID.randomUUID());
        var balanceChanged = jdbc.update("""
                UPDATE tradesphere.inventory_balances SET on_hand = on_hand - :consumed, reserved = reserved - :quantity, updated_at = now()
                WHERE organization_id = :organizationId AND warehouse_id = :warehouseId AND product_id = :productId
                  AND reserved >= :quantity AND on_hand >= :consumed
                """, parameters);
        if (balanceChanged != 1) return false;
        var reservationChanged = jdbc.update("""
                UPDATE tradesphere.stock_reservations SET status = :status, closed_by = :actorId, closed_at = now()
                WHERE organization_id = :organizationId AND order_id = :orderId AND product_id = :productId
                  AND warehouse_id = :warehouseId AND quantity = :quantity AND status = 'ACTIVE'
                """, parameters);
        if (reservationChanged != 1) return false;
        jdbc.update("""
                INSERT INTO tradesphere.order_inventory_movements
                    (id,organization_id,order_id,warehouse_id,product_id,kind,quantity,on_hand_delta,reserved_delta,actor_id)
                VALUES (:id,:organizationId,:orderId,:warehouseId,:productId,:kind,:quantity,-:consumed,-:quantity,:actorId)
                """, parameters);
        return true;
    }

    public List<OrderInventoryMovement> movements(UUID organizationId, UUID orderId) {
        return jdbc.query("""
                SELECT m.*,i.sku,i.product_name,u.username FROM tradesphere.order_inventory_movements m
                JOIN tradesphere.order_items i ON i.organization_id = m.organization_id AND i.order_id = m.order_id AND i.product_id = m.product_id
                JOIN tradesphere.users u ON u.organization_id = m.organization_id AND u.id = m.actor_id
                WHERE m.organization_id = :organizationId AND m.order_id = :orderId ORDER BY m.occurred_at,m.product_id
                """, scope(organizationId, orderId), (rs, row) -> new OrderInventoryMovement(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        rs.getObject("warehouse_id", UUID.class), rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("product_name"),
                        rs.getString("kind"), rs.getLong("quantity"), rs.getLong("on_hand_delta"), rs.getLong("reserved_delta"),
                        rs.getObject("actor_id", UUID.class), rs.getString("username"), rs.getTimestamp("occurred_at").toInstant()));
    }

    private static MapSqlParameterSource scope(UUID organizationId, UUID orderId) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("orderId", orderId);
    }
    public record Reservation(UUID productId, long quantity, String status) { }
}
