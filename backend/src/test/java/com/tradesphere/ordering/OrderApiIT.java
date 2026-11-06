package com.tradesphere.ordering;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tradesphere.catalog.Product;
import com.tradesphere.catalog.ProductService;
import com.tradesphere.catalog.ProductValues;
import com.tradesphere.catalog.SellingPriceService;
import com.tradesphere.inventory.InventoryService;
import com.tradesphere.inventory.StockReceiptRequest;
import com.tradesphere.inventory.Warehouse;
import com.tradesphere.inventory.WarehouseService;
import com.tradesphere.inventory.WarehouseValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tradesphere.bootstrap.enabled=false")
class OrderApiIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long MAX_QUANTITY = 9007199254740991L;
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ProductService products;
    @Autowired private SellingPriceService prices;
    @Autowired private CustomerService customers;
    @Autowired private WarehouseService warehouses;
    @Autowired private InventoryService inventory;
    private UUID organizationId;
    private UUID otherOrganizationId;
    private UUID actorId;
    private String writer;
    private String viewer;
    private String legacyEditor;
    private String outsider;
    private String password;
    private Customer customer;
    private Customer foreignCustomer;
    private Warehouse warehouse;
    private Warehouse foreignWarehouse;
    private Product first;
    private Product second;
    private Product foreignProduct;
    private String failureObjectName;

    @BeforeEach
    void fixtures() {
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        writer = "orders-" + UUID.randomUUID();
        viewer = "viewer-" + UUID.randomUUID();
        legacyEditor = "legacy-" + UUID.randomUUID();
        outsider = "other-" + UUID.randomUUID();
        password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tradesphere.organizations (id, name) VALUES (?, ?), (?, ?)",
                organizationId, "Fictional Order Test Organization", otherOrganizationId, "Other Fictional Organization");
        var hash = encoder.encode(password);
        user(actorId, writer, organizationId, hash, true);
        user(UUID.randomUUID(), viewer, organizationId, hash, false);
        user(UUID.randomUUID(), outsider, otherOrganizationId, hash, true);
        // Existing provisioning paths do not silently gain the new order-write permission.
        jdbc.update("""
                INSERT INTO tradesphere.users (id, organization_id, username, password_hash,
                    can_write_catalog, can_write_inventory, can_write_customers, can_write_prices)
                VALUES (?, ?, ?, ?, true, true, true, true)
                """, UUID.randomUUID(), organizationId, legacyEditor, hash);
        customer = customers.create(organizationId, customerValues("Fictional hardware customer", true));
        foreignCustomer = customers.create(otherOrganizationId, customerValues("Private customer", true));
        warehouse = warehouses.create(organizationId, new WarehouseValues("ORDER-WH", "Fictional order warehouse", null, true));
        foreignWarehouse = warehouses.create(otherOrganizationId, new WarehouseValues("ORDER-WH", "Private warehouse", null, true));
        first = products.create(organizationId, productValues("BOLT-1", "Test bolt", true));
        second = products.create(organizationId, productValues("TAPE-1", "Test tape", true));
        foreignProduct = products.create(otherOrganizationId, productValues("BOLT-1", "Private product", true));
        prices.update(organizationId, first.id(), "12.50", 0);
        prices.update(organizationId, second.id(), "0.10", 0);
        prices.update(otherOrganizationId, foreignProduct.id(), "1.00", 0);
    }

    @AfterEach
    void cleanOwnFixtures() {
        removeFailureTrigger();
        for (var table : new String[]{"stock_reservations", "order_items", "orders", "stock_movements", "inventory_balances",
                "warehouses", "products", "customers", "users", "organizations"}) {
            var column = table.equals("organizations") ? "id" : "organization_id";
            jdbc.update("DELETE FROM tradesphere." + table + " WHERE " + column + " IN (?, ?)", organizationId, otherOrganizationId);
        }
    }

    @Test
    void createsSingleAndMultiItemPendingOrdersWithExactServerOwnedSnapshots() throws Exception {
        var client = signedIn(writer);
        var single = create(client, List.of(item(first, 2)));
        assertThat(single.path("status").asString()).isEqualTo("PENDING");
        assertThat(single.path("totalAmount").asString()).isEqualTo("25.00");
        assertThat(single.path("items").size()).isEqualTo(1);
        assertThat(single.path("confirmedAt").isNull()).isTrue();
        var body = new HashMap<>(request(UUID.randomUUID(), List.of(item(first, 2), item(second, 3))));
        body.put("totalAmount", "0.01");
        body.put("organizationId", otherOrganizationId);
        var response = client.send("POST", "/orders", body, true);
        assertThat(response.statusCode()).isEqualTo(201);
        var order = json(response).path("order");
        assertThat(order.path("currency").asString()).isEqualTo("LKR");
        assertThat(order.path("totalAmount").isString()).isTrue();
        assertThat(order.path("totalAmount").asString()).isEqualTo("25.30");
        assertThat(order.path("createdBy").asString()).isEqualTo(actorId.toString());
        assertThat(order.path("customerName").asString()).isEqualTo(customer.name());
        assertThat(order.path("items").size()).isEqualTo(2);
        assertThat(line(order, first.id()).path("unitPrice").asString()).isEqualTo("12.50");
        assertThat(line(order, second.id()).path("lineTotal").asString()).isEqualTo("0.30");
        assertThat(client.send("GET", "/orders/" + order.path("id").asString(), null, false).statusCode()).isEqualTo(200);
        assertThat(orderCount()).isEqualTo(2);
        assertThat(reservationCount()).isZero();
    }

    @Test
    void listSearchStatusAndPaginationRemainOrganizationScoped() throws Exception {
        var client = signedIn(writer);
        create(client, List.of(item(first, 1)));
        var page = json(client.send("GET", "/orders?q=hardware&status=PENDING&page=0&size=1", null, false));
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("items").size()).isEqualTo(1);
        assertThat(json(client.send("GET", "/orders?status=CONFIRMED", null, false)).path("totalElements").asLong()).isZero();
        for (var path : new String[]{"/orders?page=-1", "/orders?size=101", "/orders?status=INVALID", "/orders?q=" + "a".repeat(101)}) {
            assertThat(client.send("GET", path, null, false).statusCode()).as(path).isEqualTo(400);
        }
    }

    @Test
    void rejectsInvalidQuantitiesDuplicateProductsMissingFieldsAndOversizedOrders() throws Exception {
        var client = signedIn(writer);
        for (var quantity : new Object[]{0, -1, 1.5, MAX_QUANTITY + 1}) {
            var body = request(UUID.randomUUID(), List.of(Map.of("productId", first.id(), "quantity", quantity)));
            assertThat(client.send("POST", "/orders", body, true).statusCode()).as("quantity %s", quantity).isEqualTo(400);
        }
        var bodies = new ArrayList<Map<String, Object>>();
        bodies.add(request(UUID.randomUUID(), List.of()));
        bodies.add(request(UUID.randomUUID(), List.of(item(first, 1), item(first, 2))));
        bodies.add(request(UUID.randomUUID(), List.of(Map.of("productId", first.id()))));
        bodies.add(request(UUID.randomUUID(), List.of(Map.of("quantity", 1))));
        bodies.add(request(UUID.randomUUID(), java.util.Collections.nCopies(101, item(first, 1))));
        bodies.add(Map.of("customerId", customer.id(), "warehouseId", warehouse.id(), "items", List.of(item(first, 1))));
        bodies.add(Map.of("requestId", UUID.randomUUID(), "warehouseId", warehouse.id(), "items", List.of(item(first, 1))));
        var nullItems = new HashMap<>(request(UUID.randomUUID(), List.of(item(first, 1))));
        nullItems.put("items", null);
        bodies.add(nullItems);
        var nullLine = new ArrayList<Map<String, Object>>();
        nullLine.add(null);
        bodies.add(request(UUID.randomUUID(), nullLine));
        for (var body : bodies) assertThat(client.send("POST", "/orders", body, true).statusCode()).isEqualTo(400);
        assertThat(orderCount()).isZero();
        assertThat(reservationCount()).isZero();
    }

    @Test
    void allowsZeroPriceAndSafeIntegerQuantityWithExactLargeTotals() throws Exception {
        var client = signedIn(writer);
        prices.update(organizationId, second.id(), "0", 1);
        var order = create(client, List.of(item(first, MAX_QUANTITY), item(second, 1)));
        var expected = new BigDecimal("12.50").multiply(BigDecimal.valueOf(MAX_QUANTITY)).toPlainString();
        assertThat(order.path("totalAmount").asString()).isEqualTo(expected);
        assertThat(line(order, second.id()).path("unitPrice").asString()).isEqualTo("0.00");
        assertThat(line(order, first.id()).path("quantity").asLong()).isEqualTo(MAX_QUANTITY);
    }

    @Test
    void rejectsAbsentInactiveAndUnpricedSourcesWithoutCreatingAnOrder() throws Exception {
        var client = signedIn(writer);
        var missingCustomer = new HashMap<>(request(UUID.randomUUID(), List.of(item(first, 1))));
        missingCustomer.put("customerId", UUID.randomUUID());
        assertThat(client.send("POST", "/orders", missingCustomer, true).statusCode()).isEqualTo(404);
        var missingWarehouse = new HashMap<>(request(UUID.randomUUID(), List.of(item(first, 1))));
        missingWarehouse.put("warehouseId", UUID.randomUUID());
        assertThat(client.send("POST", "/orders", missingWarehouse, true).statusCode()).isEqualTo(404);
        assertThat(client.send("POST", "/orders", request(UUID.randomUUID(), List.of(Map.of("productId", UUID.randomUUID(), "quantity", 1))), true).statusCode()).isEqualTo(404);
        var unpriced = products.create(organizationId, productValues("UNPRICED", "Unpriced test product", true));
        assertThat(client.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(unpriced, 1))), true).statusCode()).isEqualTo(409);
        customers.update(organizationId, customer.id(), customerValues(customer.name(), false), 0);
        assertThat(client.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(first, 1))), true).statusCode()).isEqualTo(409);
        customers.update(organizationId, customer.id(), customerValues(customer.name(), true), 1);
        warehouses.update(organizationId, warehouse.id(), new WarehouseValues(warehouse.code(), warehouse.name(), null, false), 0);
        assertThat(client.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(first, 1))), true).statusCode()).isEqualTo(409);
        warehouses.update(organizationId, warehouse.id(), new WarehouseValues(warehouse.code(), warehouse.name(), null, true), 1);
        products.update(organizationId, first.id(), productValues(first.sku(), first.name(), false), 0);
        assertThat(client.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(first, 1))), true).statusCode()).isEqualTo(409);
        assertThat(orderCount()).isZero();
    }

    @Test
    void priceSnapshotsAndTotalsSurviveLaterCatalogChangesAndConfirmation() throws Exception {
        var client = signedIn(writer);
        stock(first, 10); stock(second, 10);
        var order = create(client, List.of(item(first, 2), item(second, 3)));
        prices.update(organizationId, first.id(), "99.99", 1);
        products.update(organizationId, first.id(), productValues("CHANGED-SKU", "Changed product name", true), 0);
        var confirmed = json(confirm(client, id(order))).path("order");
        assertThat(confirmed.path("status").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.path("totalAmount").asString()).isEqualTo("25.30");
        assertThat(line(confirmed, first.id()).path("unitPrice").asString()).isEqualTo("12.50");
        assertThat(line(confirmed, first.id()).path("productName").asString()).isEqualTo(first.name());
        assertThat(line(confirmed, first.id()).path("sku").asString()).isEqualTo(first.sku());
        assertThat(confirmed.path("confirmedBy").asString()).isEqualTo(actorId.toString());
        assertThat(confirmed.path("confirmedAt").isNull()).isFalse();
        assertBalance(first, 10, 2, 8);
        assertBalance(second, 10, 3, 7);
        assertThat(reservationCount()).isEqualTo(2);
    }

    @Test
    void insufficientLaterItemRollsBackEarlierReservationAndLeavesOrderPending() throws Exception {
        var ordered = sortedProducts();
        stock(ordered.get(0), 10); stock(ordered.get(1), 1);
        var client = signedIn(writer);
        var order = create(client, List.of(item(ordered.get(0), 4), item(ordered.get(1), 2)));
        var response = confirm(client, id(order));
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).path("detail").asString()).contains(ordered.get(1).sku(), ordered.get(1).name(), "No items were reserved");
        assertBalance(ordered.get(0), 10, 0, 10);
        assertBalance(ordered.get(1), 1, 0, 1);
        assertPending(id(order));
        assertThat(reservationCount()).isZero();
    }

    @Test
    void missingBalanceIsInsufficientAndConfirmationRechecksInactiveData() throws Exception {
        var client = signedIn(writer);
        var order = create(client, List.of(item(first, 1)));
        assertThat(confirm(client, id(order)).statusCode()).isEqualTo(409);
        stock(first, 3);
        customers.update(organizationId, customer.id(), customerValues(customer.name(), false), 0);
        assertThat(confirm(client, id(order)).statusCode()).isEqualTo(409);
        customers.update(organizationId, customer.id(), customerValues(customer.name(), true), 1);
        warehouses.update(organizationId, warehouse.id(), new WarehouseValues(warehouse.code(), warehouse.name(), null, false), 0);
        assertThat(confirm(client, id(order)).statusCode()).isEqualTo(409);
        warehouses.update(organizationId, warehouse.id(), new WarehouseValues(warehouse.code(), warehouse.name(), null, true), 1);
        products.update(organizationId, first.id(), productValues(first.sku(), first.name(), false), 0);
        assertThat(confirm(client, id(order)).statusCode()).isEqualTo(409);
        assertBalance(first, 3, 0, 3);
        assertPending(id(order));
    }

    @Test
    void repeatedConfirmationReturnsOriginalResultEvenAfterDeactivation() throws Exception {
        var client = signedIn(writer);
        stock(first, 8);
        var order = create(client, List.of(item(first, 3)));
        var firstConfirmation = confirm(client, id(order));
        assertThat(firstConfirmation.statusCode()).isEqualTo(200);
        assertThat(json(firstConfirmation).path("replayed").asBoolean()).isFalse();
        products.update(organizationId, first.id(), productValues(first.sku(), first.name(), false), 0);
        var repeated = confirm(client, id(order));
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(json(repeated).path("replayed").asBoolean()).isTrue();
        assertThat(json(repeated).path("order").path("confirmedAt").asString())
                .isEqualTo(json(firstConfirmation).path("order").path("confirmedAt").asString());
        assertBalance(first, 8, 3, 5);
        assertThat(reservationCount()).isEqualTo(1);
    }

    @Test
    void concurrentOrdersCompetingForRemainingStockHaveOnlyOneWinner() throws Exception {
        stock(first, 5);
        var client = signedIn(writer);
        var left = create(client, List.of(item(first, 4)));
        var right = create(client, List.of(item(first, 4)));
        var leftClient = signedIn(writer);
        var rightClient = signedIn(writer);
        var responses = concurrent(() -> confirm(leftClient, id(left)), () -> confirm(rightClient, id(right)));
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
        assertBalance(first, 5, 4, 1);
        assertThat(reservationCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tradesphere.orders WHERE organization_id = ? AND status = 'CONFIRMED'", Long.class, organizationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tradesphere.orders WHERE organization_id = ? AND status = 'PENDING'", Long.class, organizationId)).isEqualTo(1);
    }

    @Test
    void concurrentSameOrderConfirmationReservesExactlyOnce() throws Exception {
        stock(first, 10); stock(second, 10);
        var order = create(signedIn(writer), List.of(item(first, 3), item(second, 2)));
        var left = signedIn(writer);
        var right = signedIn(writer);
        var responses = concurrent(() -> confirm(left, id(order)), () -> confirm(right, id(order)));
        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200);
        assertThat(responses.stream().filter(response -> json(response).path("replayed").asBoolean()).count()).isEqualTo(1);
        assertBalance(first, 10, 3, 7);
        assertBalance(second, 10, 2, 8);
        assertThat(reservationCount()).isEqualTo(2);
    }

    @Test
    void receiptAndConfirmationRetainBothPhysicalAndReservedChanges() throws Exception {
        stock(first, 5);
        var order = create(signedIn(writer), List.of(item(first, 5)));
        var confirmer = signedIn(writer);
        var inventoryReader = signedIn(writer);
        var responses = concurrent(() -> confirm(confirmer, id(order)), () -> {
            stock(first, 3);
            return inventoryReader.send("GET", "/inventory", null, false);
        });
        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200);
        assertBalance(first, 8, 5, 3);
        assertThat(reservationCount()).isEqualTo(1);
    }

    @Test
    void reverseLineOrderConcurrentConfirmationsCompleteWithoutDeadlock() throws Exception {
        stock(first, 10); stock(second, 10);
        var creator = signedIn(writer);
        var leftOrder = create(creator, List.of(item(first, 2), item(second, 3)));
        var rightOrder = create(creator, List.of(item(second, 3), item(first, 2)));
        var left = signedIn(writer);
        var right = signedIn(writer);
        var responses = concurrent(() -> confirm(left, id(leftOrder)), () -> confirm(right, id(rightOrder)));
        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200);
        assertBalance(first, 10, 4, 6);
        assertBalance(second, 10, 6, 4);
        assertThat(reservationCount()).isEqualTo(4);
    }

    @Test
    void creationRequestIdReplaysCanonicalPayloadWithoutRepricingOrDuplicating() throws Exception {
        var client = signedIn(writer);
        var requestId = UUID.randomUUID();
        var body = request(requestId, List.of(item(first, 2), item(second, 3)));
        var initial = client.send("POST", "/orders", body, true);
        assertThat(initial.statusCode()).isEqualTo(201);
        prices.update(organizationId, first.id(), "99.00", 1);
        customers.update(organizationId, customer.id(), customerValues(customer.name(), false), 0);
        var replay = client.send("POST", "/orders", request(requestId, List.of(item(second, 3), item(first, 2))), true);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json(replay).path("replayed").asBoolean()).isTrue();
        assertThat(id(json(replay).path("order"))).isEqualTo(id(json(initial).path("order")));
        assertThat(json(replay).path("order").path("totalAmount").asString()).isEqualTo("25.30");
        assertThat(client.send("POST", "/orders", request(requestId, List.of(item(first, 3))), true).statusCode()).isEqualTo(409);
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void concurrentCreationWithOneRequestIdCreatesOnePendingOrder() throws Exception {
        var requestId = UUID.randomUUID();
        var body = request(requestId, List.of(item(first, 2), item(second, 3)));
        var left = signedIn(writer);
        var right = signedIn(writer);
        var responses = concurrent(() -> left.send("POST", "/orders", body, true), () -> right.send("POST", "/orders", body, true));
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 200);
        assertThat(id(json(responses.get(0)).path("order"))).isEqualTo(id(json(responses.get(1)).path("order")));
        assertThat(orderCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tradesphere.order_items WHERE organization_id = ?", Long.class, organizationId)).isEqualTo(2);
    }

    @Test
    void unauthorizedAndLegacyWritersCannotCreateOrConfirmAndCsrfIsRequired() throws Exception {
        var order = create(signedIn(writer), List.of(item(first, 1)));
        assertThat(new ApiClient().send("GET", "/orders", null, false).statusCode()).isEqualTo(401);
        for (var username : new String[]{viewer, legacyEditor}) {
            var client = signedIn(username);
            assertThat(client.send("GET", "/orders", null, false).statusCode()).isEqualTo(200);
            assertThat(client.send("GET", "/orders/" + id(order), null, false).statusCode()).isEqualTo(200);
            assertThat(client.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(first, 1))), true).statusCode()).isEqualTo(403);
            assertThat(confirm(client, id(order)).statusCode()).isEqualTo(403);
        }
        var writerClient = signedIn(writer);
        assertThat(writerClient.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(first, 1))), false).statusCode()).isEqualTo(403);
        assertThat(writerClient.send("POST", "/orders/" + id(order) + "/confirm", null, false).statusCode()).isEqualTo(403);
        assertPending(id(order));
        assertThat(reservationCount()).isZero();
    }

    @Test
    void foreignSourcesIdentifiersAndListSearchCannotCrossOrganizationBoundary() throws Exception {
        var own = signedIn(writer);
        var order = create(own, List.of(item(first, 1)));
        var other = signedIn(outsider);
        assertThat(other.send("GET", "/orders/" + id(order), null, false).statusCode()).isEqualTo(404);
        assertThat(confirm(other, id(order)).statusCode()).isEqualTo(404);
        assertThat(json(other.send("GET", "/orders", null, false)).path("totalElements").asLong()).isZero();
        assertThat(json(other.send("GET", "/orders?q=hardware", null, false)).path("totalElements").asLong()).isZero();
        var body = new HashMap<>(request(UUID.randomUUID(), List.of(item(first, 1))));
        body.put("customerId", foreignCustomer.id());
        assertThat(own.send("POST", "/orders", body, true).statusCode()).isEqualTo(404);
        body.put("customerId", customer.id()); body.put("warehouseId", foreignWarehouse.id());
        assertThat(own.send("POST", "/orders", body, true).statusCode()).isEqualTo(404);
        assertThat(own.send("POST", "/orders", request(UUID.randomUUID(), List.of(item(foreignProduct, 1))), true).statusCode()).isEqualTo(404);
        assertPending(id(order));
        assertThat(reservationCount()).isZero();
    }

    @Test
    void compositeOrderForeignKeysRejectCrossOrganizationCustomerWarehouseAndActors() throws Exception {
        var orderId = UUID.fromString(id(create(signedIn(writer), List.of(item(first, 1)))));
        var foreignActorId = jdbc.queryForObject("SELECT id FROM tradesphere.users WHERE organization_id = ? AND username = ?",
                UUID.class, otherOrganizationId, outsider);
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.orders SET customer_id = ? WHERE organization_id = ? AND id = ?",
                foreignCustomer.id(), organizationId, orderId)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_orders_customer");
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.orders SET warehouse_id = ? WHERE organization_id = ? AND id = ?",
                foreignWarehouse.id(), organizationId, orderId)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_orders_warehouse");
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.orders SET created_by = ? WHERE organization_id = ? AND id = ?",
                foreignActorId, organizationId, orderId)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_orders_creator");
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.orders SET status = 'CONFIRMED', confirmed_at = now(), confirmed_by = ? WHERE organization_id = ? AND id = ?",
                foreignActorId, organizationId, orderId)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_orders_confirmer");
        assertPending(orderId.toString());
    }

    @Test
    void reservationForeignKeysRejectWrongWarehouseItemAndActorEvenWithinValidBalances() throws Exception {
        stock(first, 10); stock(second, 10);
        var alternateWarehouse = warehouses.create(organizationId, new WarehouseValues("OTHER-WH", "Other fictional warehouse", null, true));
        inventory.receive(organizationId, actorId, new StockReceiptRequest(UUID.randomUUID(), alternateWarehouse.id(), first.id(), 10L, null, null));
        var orderId = UUID.fromString(id(create(signedIn(writer), List.of(item(first, 1)))));
        var foreignActorId = jdbc.queryForObject("SELECT id FROM tradesphere.users WHERE organization_id = ? AND username = ?",
                UUID.class, otherOrganizationId, outsider);
        var insert = "INSERT INTO tradesphere.stock_reservations (organization_id, order_id, warehouse_id, product_id, quantity, reserved_by) VALUES (?, ?, ?, ?, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(insert, organizationId, orderId, alternateWarehouse.id(), first.id(), 1, actorId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reservations_order_warehouse");
        assertThatThrownBy(() -> jdbc.update(insert, organizationId, orderId, warehouse.id(), second.id(), 1, actorId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reservations_order_item");
        assertThatThrownBy(() -> jdbc.update(insert, organizationId, orderId, warehouse.id(), first.id(), 1, foreignActorId))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reservations_actor");
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.order_items SET product_id = ? WHERE organization_id = ? AND order_id = ?",
                foreignProduct.id(), organizationId, orderId)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_order_items_product");
        assertThat(reservationCount()).isZero();
        assertBalance(first, 10, 0, 10);
        assertBalance(second, 10, 0, 10);
    }

    @Test
    void databaseFailureAfterAnEarlierReservationAttemptRollsBackEveryEffect() throws Exception {
        var ordered = sortedProducts();
        stock(ordered.get(0), 10); stock(ordered.get(1), 10);
        var client = signedIn(writer);
        var order = create(client, List.of(item(ordered.get(0), 2), item(ordered.get(1), 3)));
        installFailureTrigger(ordered.get(1).id());
        assertThat(confirm(client, id(order)).statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT last_value FROM tradesphere." + failureObjectName + "_seq", Long.class)).isEqualTo(2);
        assertBalance(ordered.get(0), 10, 0, 10);
        assertBalance(ordered.get(1), 10, 0, 10);
        assertThat(reservationCount()).isZero();
        assertPending(id(order));
        removeFailureTrigger();
        assertThat(confirm(client, id(order)).statusCode()).isEqualTo(200);
        assertThat(reservationCount()).isEqualTo(2);
    }

    private void installFailureTrigger(UUID failingProductId) {
        failureObjectName = "test_order_failure_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SEQUENCE tradesphere." + failureObjectName + "_seq");
        jdbc.execute("""
                CREATE FUNCTION tradesphere.%s() RETURNS trigger LANGUAGE plpgsql AS $test$
                BEGIN
                    IF NEW.organization_id = '%s'::uuid THEN
                        PERFORM nextval('tradesphere.%s_seq');
                        IF NEW.product_id = '%s'::uuid THEN
                            RAISE EXCEPTION 'Simulated TRD-17 reservation insert failure';
                        END IF;
                    END IF;
                    RETURN NEW;
                END;
                $test$
                """.formatted(failureObjectName, organizationId, failureObjectName, failingProductId));
        jdbc.execute("CREATE TRIGGER " + failureObjectName + " BEFORE INSERT ON tradesphere.stock_reservations FOR EACH ROW EXECUTE FUNCTION tradesphere." + failureObjectName + "()");
    }

    private void removeFailureTrigger() {
        if (failureObjectName == null) return;
        jdbc.execute("DROP TRIGGER IF EXISTS " + failureObjectName + " ON tradesphere.stock_reservations");
        jdbc.execute("DROP FUNCTION IF EXISTS tradesphere." + failureObjectName + "()");
        jdbc.execute("DROP SEQUENCE IF EXISTS tradesphere." + failureObjectName + "_seq");
        failureObjectName = null;
    }

    private JsonNode create(ApiClient client, List<Map<String, Object>> items) throws Exception {
        var response = client.send("POST", "/orders", request(UUID.randomUUID(), items), true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json(response).path("order");
    }

    private Map<String, Object> request(UUID requestId, List<Map<String, Object>> items) {
        return Map.of("requestId", requestId, "customerId", customer.id(), "warehouseId", warehouse.id(), "items", items);
    }

    private static Map<String, Object> item(Product product, long quantity) { return Map.of("productId", product.id(), "quantity", quantity); }
    private static String id(JsonNode order) { return order.path("id").asString(); }
    private static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private static JsonNode line(JsonNode order, UUID productId) {
        var lines = order.path("items");
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            if (line.path("productId").asString().equals(productId.toString())) return line;
        }
        throw new AssertionError("Expected order item for product " + productId);
    }
    private static ProductValues productValues(String sku, String name, boolean active) { return new ProductValues(sku, name, null, "Hardware", "Each", active); }
    private static CustomerValues customerValues(String name, boolean active) { return new CustomerValues(name, null, null, null, null, active); }
    private HttpResponse<String> confirm(ApiClient client, String id) throws Exception { return client.send("POST", "/orders/" + id + "/confirm", null, true); }
    private List<Product> sortedProducts() { return List.of(first, second).stream().sorted(Comparator.comparing(product -> product.id().toString())).toList(); }
    private void stock(Product product, long quantity) { inventory.receive(organizationId, actorId, new StockReceiptRequest(UUID.randomUUID(), warehouse.id(), product.id(), quantity, "Fictional order test", null)); }
    private long orderCount() { return jdbc.queryForObject("SELECT count(*) FROM tradesphere.orders WHERE organization_id = ?", Long.class, organizationId); }
    private long reservationCount() { return jdbc.queryForObject("SELECT count(*) FROM tradesphere.stock_reservations WHERE organization_id = ?", Long.class, organizationId); }
    private void assertPending(String id) { assertThat(jdbc.queryForObject("SELECT status FROM tradesphere.orders WHERE organization_id = ? AND id = ?::uuid", String.class, organizationId, id)).isEqualTo("PENDING"); }

    private void assertBalance(Product product, long onHand, long reserved, long available) {
        var balance = jdbc.queryForMap("SELECT on_hand, reserved, available FROM tradesphere.inventory_balances WHERE organization_id = ? AND warehouse_id = ? AND product_id = ?",
                organizationId, warehouse.id(), product.id());
        assertThat(((Number) balance.get("on_hand")).longValue()).isEqualTo(onHand);
        assertThat(((Number) balance.get("reserved")).longValue()).isEqualTo(reserved);
        assertThat(((Number) balance.get("available")).longValue()).isEqualTo(available);
    }

    private List<HttpResponse<String>> concurrent(Callable<HttpResponse<String>> left, Callable<HttpResponse<String>> right) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of(left, right).stream().map(action -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start timed out");
                return action.call();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<HttpResponse<String>>();
            for (var task : tasks) results.add(task.get(20, TimeUnit.SECONDS));
            return results;
        }
    }

    private void user(UUID id, String username, UUID organization, String hash, boolean orderWrite) {
        jdbc.update("INSERT INTO tradesphere.users (id, organization_id, username, password_hash, can_write_orders) VALUES (?, ?, ?, ?, ?)", id, organization, username, hash, orderWrite);
    }

    private ApiClient signedIn(String username) throws Exception { var client = new ApiClient(); client.login(username); return client; }

    private class ApiClient {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf;
        private String header;

        void token() throws Exception {
            var body = json(send("GET", "/auth/csrf", null, false));
            csrf = body.path("token").asString(); header = body.path("headerName").asString();
        }

        void login(String username) throws Exception {
            token();
            var body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            var response = http.send(HttpRequest.newBuilder(uri("/auth/login")).header(header, csrf).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(204);
            token();
        }

        HttpResponse<String> send(String method, String path, Object body, boolean secure) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15));
            if (secure) request.header(header, csrf);
            if (body != null) request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api/v1" + path); }
    }
}
