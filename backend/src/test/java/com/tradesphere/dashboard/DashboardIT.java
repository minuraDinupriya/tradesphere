package com.tradesphere.dashboard;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties="tradesphere.bootstrap.enabled=false")
class DashboardIT {
    @Autowired DashboardService dashboard;
    @Autowired JdbcTemplate jdbc;

    @Test
    void summarizesOnlyTheRequestedOrganizationIncludingInactiveAndZeroAvailabilitySemantics() {
        var org=UUID.randomUUID(); var other=UUID.randomUUID();
        jdbc.update("INSERT INTO tradesphere.organizations VALUES (?, 'Fictional dashboard'), (?, 'Other fictional dashboard')",org,other);
        var warehouse=UUID.randomUUID(); var product=UUID.randomUUID();
        try {
            for (var tenant : new UUID[]{org,other}) {
                jdbc.update("INSERT INTO tradesphere.products(id,organization_id,sku,name,category,unit_of_measure,active) VALUES (?,?, 'BOLT','Fictional bolt','Hardware','Each',true)", tenant.equals(org)?product:UUID.randomUUID(),tenant);
            }
            jdbc.update("INSERT INTO tradesphere.warehouses(id,organization_id,code,name,active) VALUES (?,?,'WH','Fictional warehouse',false)",warehouse,org);
            jdbc.update("INSERT INTO tradesphere.inventory_balances(organization_id,warehouse_id,product_id,on_hand,reserved) VALUES (?,?,?,4,4)",org,warehouse,product);
            var summary=dashboard.summary(org);
            assertThat(((Number)summary.counts().get("activeProducts")).longValue()).isEqualTo(1);
            assertThat(((Number)summary.counts().get("warehouses")).longValue()).isEqualTo(1);
            assertThat(((Number)summary.counts().get("zeroAvailableBalances")).longValue()).isEqualTo(1);
            assertThat(summary.recentOrders()).isEmpty();
            assertThat(((Number)dashboard.summary(other).counts().get("warehouses")).longValue()).isZero();
            assertThat(dashboard.summary(UUID.randomUUID()).counts().values()).allSatisfy(value -> assertThat(((Number)value).longValue()).isZero());
        } finally {
            for(var table:new String[]{"inventory_balances","warehouses","products","organizations"})
                jdbc.update("DELETE FROM tradesphere."+table+" WHERE "+(table.equals("organizations")?"id":"organization_id")+" IN (?,?)",org,other);
        }
    }
}
