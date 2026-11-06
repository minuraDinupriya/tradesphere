package com.tradesphere.inventory;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Inventory operations used only inside Ordering's create/confirm transaction. */
@Service
public class InventoryOrderAccess {
    private final WarehouseRepository warehouses;
    private final NamedParameterJdbcTemplate jdbc;
    public InventoryOrderAccess(WarehouseRepository warehouses, NamedParameterJdbcTemplate jdbc) {
        this.warehouses = warehouses;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Warehouse lockActiveWarehouse(UUID organizationId, UUID warehouseId) {
        var warehouse = warehouses.find(organizationId, warehouseId, true).orElseThrow(WarehouseService::notFound);
        if (!warehouse.active()) throw new InventoryException(HttpStatus.CONFLICT, "Orders require an active warehouse.");
        return warehouse;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(UUID organizationId, UUID orderId, UUID warehouseId, UUID actorId, List<ReservationItem> items) {
        // All product eligibility locks must already be held. Balance writes follow the same UUID order for every order.
        for (var item : items.stream().sorted(Comparator.comparing(line -> line.productId().toString())).toList()) {
            var parameters = new MapSqlParameterSource("organizationId", organizationId).addValue("orderId", orderId)
                    .addValue("warehouseId", warehouseId).addValue("productId", item.productId())
                    .addValue("quantity", item.quantity()).addValue("actorId", actorId);
            var changed = jdbc.update("""
                    UPDATE tradesphere.inventory_balances SET reserved = reserved + :quantity, updated_at = now()
                    WHERE organization_id = :organizationId AND warehouse_id = :warehouseId AND product_id = :productId
                      AND on_hand - reserved >= :quantity
                    """, parameters);
            if (changed == 0) throw new InventoryException(HttpStatus.CONFLICT,
                    "Insufficient available stock for product " + item.productId() + ". No stock was reserved for this order.");
            jdbc.update("""
                    INSERT INTO tradesphere.stock_reservations (organization_id, order_id, warehouse_id, product_id, quantity, reserved_by)
                    VALUES (:organizationId, :orderId, :warehouseId, :productId, :quantity, :actorId)
                    """, parameters);
        }
    }

    public record ReservationItem(UUID productId, long quantity) { }
}
