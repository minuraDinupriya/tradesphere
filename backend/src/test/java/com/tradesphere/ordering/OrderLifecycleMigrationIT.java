package com.tradesphere.ordering;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tradesphere.bootstrap.enabled=false")
class OrderLifecycleMigrationIT {
    @Autowired private JdbcTemplate admin;
    @Autowired private Environment environment;

    @Test
    void upgradePreservesExistingOrdersReservationsAndRecordedMetadata() {
        // Use a unique disposable database: never downgrade or rewrite the configured application's migrations.
        var database = "tradesphere_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var url = environment.getRequiredProperty("spring.datasource.url");
        var dataSource = new DriverManagerDataSource(url.substring(0, url.lastIndexOf('/') + 1) + database,
                environment.getRequiredProperty("spring.datasource.username"), environment.getRequiredProperty("spring.datasource.password"));
        admin.execute("CREATE DATABASE " + database);
        try {
            Flyway.configure().dataSource(dataSource).defaultSchema("public").target("6").load().migrate();
            var jdbc = new JdbcTemplate(dataSource);
            var org = UUID.randomUUID(); var actor = UUID.randomUUID(); var product = UUID.randomUUID();
            var customer = UUID.randomUUID(); var warehouse = UUID.randomUUID();
            var pending = UUID.randomUUID(); var confirmed = UUID.randomUUID();
            jdbc.update("INSERT INTO tradesphere.organizations VALUES (?, 'Fictional upgrade fixture')", org);
            jdbc.update("INSERT INTO tradesphere.users(id,organization_id,username,password_hash) VALUES (?,?,'upgrade-fixture','disabled-fixture-hash')", actor, org);
            jdbc.update("INSERT INTO tradesphere.products(id,organization_id,sku,name,category,unit_of_measure,selling_price) VALUES (?,?,'BOLT','Fictional bolt','Hardware','Each',12.50)", product, org);
            jdbc.update("INSERT INTO tradesphere.customers(id,organization_id,name) VALUES (?,?,'Fictional customer')", customer, org);
            jdbc.update("INSERT INTO tradesphere.warehouses(id,organization_id,code,name) VALUES (?,?,'UPGRADE','Fictional warehouse')", warehouse, org);
            jdbc.update("INSERT INTO tradesphere.inventory_balances(organization_id,warehouse_id,product_id,on_hand,reserved) VALUES (?,?,?,10,2)", org, warehouse, product);
            for (var id : new UUID[]{pending, confirmed}) {
                jdbc.update("""
                        INSERT INTO tradesphere.orders(id,organization_id,request_id,customer_id,customer_name,warehouse_id,warehouse_name,warehouse_code,total_amount,created_by,created_at)
                        VALUES (?,?,?,?, 'Fictional customer',?, 'Fictional warehouse','UPGRADE',25.00,?,'2026-11-01T08:00:00Z')
                        """, id, org, UUID.randomUUID(), customer, warehouse, actor);
                jdbc.update("INSERT INTO tradesphere.order_items(organization_id,order_id,product_id,sku,product_name,unit_of_measure,quantity,unit_price) VALUES (?,?,?,'BOLT','Fictional bolt','Each',2,12.50)", org, id, product);
            }
            jdbc.update("UPDATE tradesphere.orders SET status='CONFIRMED',confirmed_by=?,confirmed_at='2026-11-01T09:00:00Z' WHERE id=?", actor, confirmed);
            jdbc.update("INSERT INTO tradesphere.stock_reservations(organization_id,order_id,warehouse_id,product_id,quantity,reserved_by) VALUES (?,?,?,?,2,?)", org, confirmed, warehouse, product, actor);
            var orders = jdbc.queryForList("SELECT * FROM tradesphere.orders ORDER BY id");
            var balances = jdbc.queryForList("SELECT * FROM tradesphere.inventory_balances");

            Flyway.configure().dataSource(dataSource).defaultSchema("public").target("7").load().migrate();

            assertThat(jdbc.queryForList("SELECT * FROM tradesphere.orders ORDER BY id")).isEqualTo(orders);
            assertThat(jdbc.queryForList("SELECT * FROM tradesphere.inventory_balances")).isEqualTo(balances);
            assertThat(jdbc.queryForObject("SELECT status FROM tradesphere.stock_reservations", String.class)).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tradesphere.order_inventory_movements", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM tradesphere.order_state_history WHERE backfilled", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM tradesphere.order_state_history h JOIN tradesphere.orders o ON o.organization_id=h.organization_id AND o.id=h.order_id
                    WHERE h.actor_id = CASE WHEN h.to_status='PENDING' THEN o.created_by ELSE o.confirmed_by END
                      AND h.occurred_at = CASE WHEN h.to_status='PENDING' THEN o.created_at ELSE o.confirmed_at END
                    """, Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT can_write_orders FROM tradesphere.users", Boolean.class)).isFalse();
        } finally {
            admin.execute("DROP DATABASE " + database);
        }
    }
}
