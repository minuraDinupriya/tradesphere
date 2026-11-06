package com.tradesphere.ordering;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.tradesphere.inventory.Warehouse;

@Repository
public class OrderRepository {
    private static final String FILTER = """
            FROM tradesphere.orders WHERE organization_id = :organizationId
              AND (:q = '' OR customer_name ILIKE :pattern ESCAPE '!' OR warehouse_name ILIKE :pattern ESCAPE '!'
                   OR id::text ILIKE :pattern ESCAPE '!')
              AND (:status = '' OR status = :status)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public OrderRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<OrderSummary> find(UUID organizationId, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM tradesphere.orders WHERE organization_id = :organizationId AND id = :id" + (lock ? " FOR UPDATE" : ""),
                scope(organizationId).addValue("id", id), OrderRepository::map).stream().findFirst();
    }

    public Optional<OrderSummary> byRequest(UUID organizationId, UUID requestId) {
        return jdbc.query("SELECT * FROM tradesphere.orders WHERE organization_id = :organizationId AND request_id = :requestId",
                scope(organizationId).addValue("requestId", requestId), OrderRepository::map).stream().findFirst();
    }

    public Optional<OrderSummary> insert(UUID organizationId, UUID actorId, OrderCreateRequest request, Customer customer,
                                         Warehouse warehouse, BigDecimal total) {
        return jdbc.query("""
                INSERT INTO tradesphere.orders (id, organization_id, request_id, customer_id, customer_name, warehouse_id,
                    warehouse_name, warehouse_code, total_amount, created_by)
                VALUES (:id, :organizationId, :requestId, :customerId, :customerName, :warehouseId, :warehouseName, :warehouseCode, :total, :actorId)
                ON CONFLICT (organization_id, request_id) DO NOTHING RETURNING *
                """, scope(organizationId).addValue("id", UUID.randomUUID()).addValue("requestId", request.requestId())
                        .addValue("customerId", customer.id()).addValue("customerName", customer.name())
                        .addValue("warehouseId", warehouse.id()).addValue("warehouseName", warehouse.name()).addValue("warehouseCode", warehouse.code())
                        .addValue("total", total).addValue("actorId", actorId), OrderRepository::map).stream().findFirst();
    }

    public void insertItems(UUID organizationId, UUID orderId, List<OrderItem> items) {
        for (var item : items) {
            jdbc.update("""
                    INSERT INTO tradesphere.order_items (organization_id, order_id, product_id, sku, product_name, unit_of_measure, quantity, unit_price)
                    VALUES (:organizationId, :orderId, :productId, :sku, :name, :unit, :quantity, :price)
                    """, scope(organizationId).addValue("orderId", orderId).addValue("productId", item.productId()).addValue("sku", item.sku())
                            .addValue("name", item.productName()).addValue("unit", item.unitOfMeasure()).addValue("quantity", item.quantity())
                            .addValue("price", new BigDecimal(item.unitPrice())));
        }
    }

    public List<OrderItem> items(UUID organizationId, UUID orderId) {
        return jdbc.query("SELECT * FROM tradesphere.order_items WHERE organization_id = :organizationId AND order_id = :orderId ORDER BY product_id",
                scope(organizationId).addValue("orderId", orderId), (rs, row) -> new OrderItem(rs.getObject("product_id", UUID.class), rs.getString("sku"),
                        rs.getString("product_name"), rs.getString("unit_of_measure"), rs.getLong("quantity"),
                        rs.getBigDecimal("unit_price").toPlainString(), rs.getBigDecimal("line_total").toPlainString()));
    }

    public void confirm(UUID organizationId, UUID id, UUID actorId) {
        var changed = jdbc.update("""
                UPDATE tradesphere.orders SET status = 'CONFIRMED', confirmed_by = :actorId, confirmed_at = now()
                WHERE organization_id = :organizationId AND id = :id AND status = 'PENDING'
                """, scope(organizationId).addValue("id", id).addValue("actorId", actorId));
        if (changed != 1) throw new IllegalStateException("Locked pending order could not be confirmed.");
    }

    public List<OrderSummary> list(UUID organizationId, String q, String status, int page, int size) {
        return jdbc.query("SELECT * " + FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                filter(organizationId, q, status).addValue("limit", size).addValue("offset", (long) page * size), OrderRepository::map);
    }

    public long count(UUID organizationId, String q, String status) {
        return jdbc.queryForObject("SELECT count(*) " + FILTER, filter(organizationId, q, status), Long.class);
    }

    private static MapSqlParameterSource scope(UUID organizationId) { return new MapSqlParameterSource("organizationId", organizationId); }
    private static MapSqlParameterSource filter(UUID organizationId, String q, String status) {
        var pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return scope(organizationId).addValue("q", q).addValue("pattern", pattern).addValue("status", status);
    }

    private static OrderSummary map(ResultSet rs, int row) throws SQLException {
        var confirmed = rs.getTimestamp("confirmed_at");
        return new OrderSummary(rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class), rs.getObject("customer_id", UUID.class),
                rs.getString("customer_name"), rs.getObject("warehouse_id", UUID.class), rs.getString("warehouse_name"), rs.getString("warehouse_code"),
                rs.getString("status"), rs.getString("currency"), rs.getBigDecimal("total_amount").toPlainString(),
                rs.getTimestamp("created_at").toInstant(), confirmed == null ? null : confirmed.toInstant(),
                rs.getObject("created_by", UUID.class), rs.getObject("confirmed_by", UUID.class));
    }
}
