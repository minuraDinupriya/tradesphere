package com.tradesphere.dashboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only operational projection; business mutations remain owned by their capabilities. */
@Service
public class DashboardService {
    private final NamedParameterJdbcTemplate jdbc;
    public DashboardService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(UUID organizationId) {
        var scope = new MapSqlParameterSource("org", organizationId);
        var counts = jdbc.queryForMap("""
                SELECT
                  (SELECT count(*) FROM tradesphere.products WHERE organization_id=:org AND active) AS "activeProducts",
                  (SELECT count(*) FROM tradesphere.warehouses WHERE organization_id=:org) AS "warehouses",
                  (SELECT count(*) FROM tradesphere.customers WHERE organization_id=:org AND active) AS "activeCustomers",
                  (SELECT count(*) FROM tradesphere.inventory_balances WHERE organization_id=:org) AS "recordedBalances",
                  (SELECT count(*) FROM tradesphere.inventory_balances WHERE organization_id=:org AND available=0) AS "zeroAvailableBalances",
                  (SELECT count(*) FROM tradesphere.orders WHERE organization_id=:org) AS "totalOrders"
                """, scope);
        var statuses = jdbc.query("""
                SELECT status,count(*) AS total FROM tradesphere.orders
                WHERE organization_id=:org GROUP BY status ORDER BY status
                """, scope, (rs, row) -> new StatusCount(rs.getString("status"), rs.getLong("total")));
        var recent = jdbc.query("""
                SELECT id,customer_name,status,created_at FROM tradesphere.orders
                WHERE organization_id=:org ORDER BY created_at DESC,id DESC LIMIT 5
                """, scope, (rs, row) -> new RecentOrder(rs.getObject("id",UUID.class), rs.getString("customer_name"),
                        rs.getString("status"),rs.getTimestamp("created_at").toInstant()));
        return new Summary(counts,statuses,recent);
    }
    public record Summary(Map<String,Object> counts, List<StatusCount> ordersByStatus, List<RecentOrder> recentOrders) { }
    public record StatusCount(String status,long total) { }
    public record RecentOrder(UUID id,String customerName,String status,java.time.Instant createdAt) { }
}
