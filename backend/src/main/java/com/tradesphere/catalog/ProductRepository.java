package com.tradesphere.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    private static final String FILTER = """
            FROM tradesphere.products
            WHERE organization_id = :organizationId
              AND (:query = '' OR name ILIKE :pattern ESCAPE '!' OR sku ILIKE :pattern ESCAPE '!')
              AND (:category = '' OR lower(category) = lower(:category))
              AND (:active IS NULL OR active = :active)
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public ProductRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Product create(UUID organizationId, ProductValues values) {
        var parameters = values(organizationId, values).addValue("id", UUID.randomUUID());
        return jdbc.queryForObject("""
                INSERT INTO tradesphere.products (id, organization_id, sku, name, description, category, unit_of_measure, active)
                VALUES (:id, :organizationId, :sku, :name, :description, :category, :unit, :active)
                RETURNING *
                """, parameters, ProductRepository::map);
    }

    public Optional<Product> find(UUID organizationId, UUID id) {
        return jdbc.query("SELECT * FROM tradesphere.products WHERE organization_id = :organizationId AND id = :id",
                new MapSqlParameterSource("organizationId", organizationId).addValue("id", id), ProductRepository::map)
                .stream().findFirst();
    }

    public Optional<Product> update(UUID organizationId, UUID id, ProductValues values, long version) {
        return jdbc.query("""
                UPDATE tradesphere.products
                SET sku = :sku, name = :name, description = :description, category = :category,
                    unit_of_measure = :unit, active = :active, version = version + 1, updated_at = now()
                WHERE organization_id = :organizationId AND id = :id AND version = :version
                RETURNING *
                """, values(organizationId, values).addValue("id", id).addValue("version", version), ProductRepository::map)
                .stream().findFirst();
    }

    public List<Product> list(UUID organizationId, String query, String category, Boolean active, int page, int size) {
        return jdbc.query("SELECT * " + FILTER + " ORDER BY lower(name), id LIMIT :limit OFFSET :offset",
                filter(organizationId, query, category, active).addValue("limit", size).addValue("offset", (long) page * size),
                ProductRepository::map);
    }

    public long count(UUID organizationId, String query, String category, Boolean active) {
        return jdbc.queryForObject("SELECT count(*) " + FILTER, filter(organizationId, query, category, active), Long.class);
    }

    public List<String> categories(UUID organizationId) {
        return jdbc.queryForList("""
                SELECT DISTINCT category FROM tradesphere.products
                WHERE organization_id = :organizationId ORDER BY category
                """, new MapSqlParameterSource("organizationId", organizationId), String.class);
    }

    private static MapSqlParameterSource values(UUID organizationId, ProductValues values) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("sku", values.sku())
                .addValue("name", values.name()).addValue("description", values.description())
                .addValue("category", values.category()).addValue("unit", values.unitOfMeasure()).addValue("active", values.active());
    }

    private static MapSqlParameterSource filter(UUID organizationId, String query, String category, Boolean active) {
        var escaped = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return new MapSqlParameterSource("organizationId", organizationId).addValue("query", query)
                .addValue("pattern", "%" + escaped + "%").addValue("category", category)
                .addValue("active", active, java.sql.Types.BOOLEAN);
    }

    private static Product map(ResultSet rs, int row) throws SQLException {
        return new Product(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("sku"), rs.getString("name"), rs.getString("description"), rs.getString("category"),
                rs.getString("unit_of_measure"), rs.getBoolean("active"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
