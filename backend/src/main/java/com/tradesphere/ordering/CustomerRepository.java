package com.tradesphere.ordering;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    private static final String FILTER = """
            FROM tradesphere.customers WHERE organization_id = :organizationId
              AND (:q = '' OR name ILIKE :pattern ESCAPE '!' OR contact_name ILIKE :pattern ESCAPE '!'
                   OR email ILIKE :pattern ESCAPE '!' OR phone ILIKE :pattern ESCAPE '!')
              AND (:active IS NULL OR active = :active)
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public CustomerRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Customer create(UUID organizationId, CustomerValues values) {
        return jdbc.queryForObject("""
                INSERT INTO tradesphere.customers (id, organization_id, name, contact_name, email, phone, address, active)
                VALUES (:id, :organizationId, :name, :contactName, :email, :phone, :address, :active) RETURNING *
                """, parameters(organizationId, values).addValue("id", UUID.randomUUID()), CustomerRepository::map);
    }

    public Optional<Customer> find(UUID organizationId, UUID id) {
        return jdbc.query("SELECT * FROM tradesphere.customers WHERE organization_id = :organizationId AND id = :id",
                new MapSqlParameterSource("organizationId", organizationId).addValue("id", id), CustomerRepository::map).stream().findFirst();
    }

    public Optional<Customer> lockForOrder(UUID organizationId, UUID id) {
        return jdbc.query("SELECT * FROM tradesphere.customers WHERE organization_id = :organizationId AND id = :id FOR SHARE",
                new MapSqlParameterSource("organizationId", organizationId).addValue("id", id), CustomerRepository::map).stream().findFirst();
    }

    public Optional<Customer> update(UUID organizationId, UUID id, CustomerValues values, long version) {
        return jdbc.query("""
                UPDATE tradesphere.customers SET name = :name, contact_name = :contactName, email = :email,
                    phone = :phone, address = :address, active = :active, version = version + 1, updated_at = now()
                WHERE organization_id = :organizationId AND id = :id AND version = :version RETURNING *
                """, parameters(organizationId, values).addValue("id", id).addValue("version", version), CustomerRepository::map).stream().findFirst();
    }

    public List<Customer> list(UUID organizationId, String q, Boolean active, int page, int size) {
        return jdbc.query("SELECT * " + FILTER + " ORDER BY lower(name), id LIMIT :limit OFFSET :offset",
                filter(organizationId, q, active).addValue("limit", size).addValue("offset", (long) page * size), CustomerRepository::map);
    }

    public long count(UUID organizationId, String q, Boolean active) {
        return jdbc.queryForObject("SELECT count(*) " + FILTER, filter(organizationId, q, active), Long.class);
    }

    private static MapSqlParameterSource parameters(UUID organizationId, CustomerValues values) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("name", values.name())
                .addValue("contactName", values.contactName()).addValue("email", values.email()).addValue("phone", values.phone())
                .addValue("address", values.address()).addValue("active", values.active());
    }

    private static MapSqlParameterSource filter(UUID organizationId, String q, Boolean active) {
        var pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new MapSqlParameterSource("organizationId", organizationId).addValue("q", q).addValue("pattern", pattern)
                .addValue("active", active, java.sql.Types.BOOLEAN);
    }

    private static Customer map(ResultSet rs, int row) throws SQLException {
        return new Customer(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("contact_name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("address"), rs.getBoolean("active"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
