package com.tradesphere.catalog;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public catalog operations used by surrounding inventory/order transactions. */
@Service
public class CatalogProductAccess {
    private final NamedParameterJdbcTemplate jdbc;
    public CatalogProductAccess(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveStatus(UUID organizationId, UUID productId) {
        return jdbc.query("SELECT active FROM tradesphere.products WHERE organization_id = :organizationId AND id = :id FOR SHARE",
                Map.of("organizationId", organizationId, "id", productId), (rs, row) -> rs.getBoolean("active"))
                .stream().findFirst().orElseThrow(ProductNotFoundException::new);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<OrderProduct> lockForOrder(UUID organizationId, List<UUID> productIds) {
        var found = jdbc.query("""
                SELECT id, sku, name, unit_of_measure, active, selling_price FROM tradesphere.products
                WHERE organization_id = :organizationId AND id IN (:ids) ORDER BY id FOR SHARE
                """, new MapSqlParameterSource("organizationId", organizationId).addValue("ids", productIds),
                (rs, row) -> new OrderProduct(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"),
                        rs.getString("unit_of_measure"), rs.getBoolean("active"), rs.getBigDecimal("selling_price")));
        if (found.size() != productIds.size()) throw new ProductNotFoundException();
        return found;
    }

    public record OrderProduct(UUID id, String sku, String name, String unitOfMeasure, boolean active, BigDecimal sellingPrice) { }
}
