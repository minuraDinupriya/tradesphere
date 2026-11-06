package com.tradesphere.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SellingPriceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public SellingPriceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<SellingPrice> find(UUID organizationId, UUID productId) {
        return jdbc.query("""
                SELECT selling_price, price_version FROM tradesphere.products
                WHERE organization_id = :organizationId AND id = :id
                """, scope(organizationId, productId), (rs, row) -> {
                    var amount = rs.getBigDecimal("selling_price");
                    return new SellingPrice(productId, "LKR", amount == null ? null : amount.toPlainString(), rs.getLong("price_version"));
                }).stream().findFirst();
    }

    public boolean update(UUID organizationId, UUID productId, SellingPriceAmount amount, long version) {
        return jdbc.update("""
                UPDATE tradesphere.products SET selling_price = :amount, price_version = price_version + 1
                WHERE organization_id = :organizationId AND id = :id AND price_version = :version
                """, scope(organizationId, productId).addValue("amount", amount.value()).addValue("version", version)) == 1;
    }

    private static MapSqlParameterSource scope(UUID organizationId, UUID productId) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("id", productId);
    }
}
