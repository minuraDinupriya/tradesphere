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
public class WarehouseRepository {
    private static final String FILTER = """
            FROM tradesphere.warehouses WHERE organization_id = :organizationId
              AND (:q = '' OR name ILIKE :pattern ESCAPE '!' OR code ILIKE :pattern ESCAPE '!' OR location ILIKE :pattern ESCAPE '!')
              AND (:active IS NULL OR active = :active)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public WarehouseRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Warehouse create(UUID organizationId, WarehouseValues values) {
        return jdbc.queryForObject("""
                INSERT INTO tradesphere.warehouses (id, organization_id, code, name, location, active)
                VALUES (:id, :organizationId, :code, :name, :location, :active) RETURNING *
                """, parameters(organizationId, values).addValue("id", UUID.randomUUID()), WarehouseRepository::map);
    }

    public Optional<Warehouse> find(UUID organizationId, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM tradesphere.warehouses WHERE organization_id = :organizationId AND id = :id"
                        + (lock ? " FOR SHARE" : ""),
                new MapSqlParameterSource("organizationId", organizationId).addValue("id", id), WarehouseRepository::map).stream().findFirst();
    }

    public Optional<Warehouse> update(UUID organizationId, UUID id, WarehouseValues values, long version) {
        return jdbc.query("""
                UPDATE tradesphere.warehouses SET code = :code, name = :name, location = :location, active = :active,
                    version = version + 1, updated_at = now()
                WHERE organization_id = :organizationId AND id = :id AND version = :version RETURNING *
                """, parameters(organizationId, values).addValue("id", id).addValue("version", version), WarehouseRepository::map).stream().findFirst();
    }

    public List<Warehouse> list(UUID organizationId, String q, Boolean active, int page, int size) {
        return jdbc.query("SELECT * " + FILTER + " ORDER BY lower(name), id LIMIT :limit OFFSET :offset",
                filter(organizationId, q, active).addValue("limit", size).addValue("offset", (long) page * size), WarehouseRepository::map);
    }

    public long count(UUID organizationId, String q, Boolean active) {
        return jdbc.queryForObject("SELECT count(*) " + FILTER, filter(organizationId, q, active), Long.class);
    }

    private static MapSqlParameterSource parameters(UUID organizationId, WarehouseValues values) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("code", values.code())
                .addValue("name", values.name()).addValue("location", values.location()).addValue("active", values.active());
    }

    private static MapSqlParameterSource filter(UUID organizationId, String q, Boolean active) {
        var pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new MapSqlParameterSource("organizationId", organizationId).addValue("q", q).addValue("pattern", pattern)
                .addValue("active", active, java.sql.Types.BOOLEAN);
    }

    private static Warehouse map(ResultSet rs, int row) throws SQLException {
        return new Warehouse(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("location"), rs.getBoolean("active"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
