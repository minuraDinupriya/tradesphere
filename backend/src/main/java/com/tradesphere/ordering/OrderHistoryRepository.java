package com.tradesphere.ordering;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public OrderHistoryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID organizationId, UUID orderId, String from, String to, UUID actorId) {
        jdbc.update("""
                INSERT INTO tradesphere.order_state_history (id,organization_id,order_id,from_status,to_status,actor_id)
                VALUES (:id,:organizationId,:orderId,:from,:to,:actorId)
                """, scope(organizationId, orderId).addValue("id", UUID.randomUUID()).addValue("from", from).addValue("to", to).addValue("actorId", actorId));
    }

    public boolean recorded(UUID organizationId, UUID orderId, String to) {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM tradesphere.order_state_history
                    WHERE organization_id = :organizationId AND order_id = :orderId AND to_status = :to)
                """, scope(organizationId, orderId).addValue("to", to), Boolean.class);
    }

    public List<OrderStateEvent> events(UUID organizationId, UUID orderId) {
        return jdbc.query("""
                SELECT h.*,u.username FROM tradesphere.order_state_history h
                JOIN tradesphere.users u ON u.organization_id = h.organization_id AND u.id = h.actor_id
                WHERE h.organization_id = :organizationId AND h.order_id = :orderId
                ORDER BY CASE h.to_status WHEN 'PENDING' THEN 0 WHEN 'CONFIRMED' THEN 1 WHEN 'PROCESSING' THEN 2 ELSE 3 END, h.occurred_at, h.id
                """, scope(organizationId, orderId), (rs, row) -> new OrderStateEvent(rs.getObject("id", UUID.class),
                        rs.getString("from_status"), rs.getString("to_status"), rs.getObject("actor_id", UUID.class),
                        rs.getString("username"), rs.getTimestamp("occurred_at").toInstant(), rs.getBoolean("backfilled")));
    }

    private static MapSqlParameterSource scope(UUID organizationId, UUID orderId) {
        return new MapSqlParameterSource("organizationId", organizationId).addValue("orderId", orderId);
    }
}
