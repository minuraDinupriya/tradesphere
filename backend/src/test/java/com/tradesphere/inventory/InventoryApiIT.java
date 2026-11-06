package com.tradesphere.inventory;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tradesphere.bootstrap.enabled=false")
class InventoryApiIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private WarehouseService warehouses;
    @Autowired private InventoryService inventory;
    @Autowired private ProductService products;
    private UUID organizationId;
    private UUID otherOrganizationId;
    private UUID actorId;
    private String writer;
    private String viewer;
    private String catalogEditor;
    private String outsider;
    private String password;
    private Warehouse warehouse;
    private Warehouse otherWarehouse;
    private Product product;
    private Product otherProduct;

    @BeforeEach
    void fixtures() {
        organizationId = UUID.randomUUID(); otherOrganizationId = UUID.randomUUID(); actorId = UUID.randomUUID();
        writer = "inventory-" + UUID.randomUUID(); viewer = "viewer-" + UUID.randomUUID();
        catalogEditor = "catalog-" + UUID.randomUUID(); outsider = "other-" + UUID.randomUUID(); password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tradesphere.organizations (id, name) VALUES (?, ?), (?, ?)", organizationId, "Inventory Test Organization", otherOrganizationId, "Other Test Organization");
        var hash = encoder.encode(password);
        user(actorId, writer, organizationId, hash, false, true);
        user(UUID.randomUUID(), viewer, organizationId, hash, false, false);
        user(UUID.randomUUID(), catalogEditor, organizationId, hash, true, false);
        user(UUID.randomUUID(), outsider, otherOrganizationId, hash, false, true);
        warehouse = warehouses.create(organizationId, new WarehouseValues("WH-1", "Main test warehouse", "Fictional location", true));
        otherWarehouse = warehouses.create(otherOrganizationId, new WarehouseValues("WH-1", "Private warehouse", null, true));
        product = products.create(organizationId, new ProductValues("BOLT-1", "Steel bolt", null, "Hardware", "Each", true));
        otherProduct = products.create(otherOrganizationId, new ProductValues("BOLT-1", "Private product", null, "Hardware", "Each", true));
    }

    @AfterEach
    void cleanOwnFixtures() {
        for (var table : new String[]{"stock_movements", "inventory_balances", "warehouses", "products", "users", "organizations"}) {
            var column = table.equals("organizations") ? "id" : "organization_id";
            jdbc.update("DELETE FROM tradesphere." + table + " WHERE " + column + " IN (?, ?)", organizationId, otherOrganizationId);
        }
    }

    @Test
    void warehousesAreValidatedNormalizedPagedAndVersionChecked() throws Exception {
        var client = signedIn(writer);
        var response = client.send("POST", "/warehouses", Map.of("code", "col-01", "name", " Colombo hub ", "location", " ", "active", true), true);
        assertThat(response.statusCode()).isEqualTo(201);
        var id = json(response).path("id").asString();
        assertThat(json(response).path("code").asString()).isEqualTo("COL-01");
        assertThat(json(response).path("name").asString()).isEqualTo("Colombo hub");
        var update = Map.of("code", "COL-01", "name", "Updated hub", "location", "Fictional city", "active", false, "version", 0);
        assertThat(client.send("PUT", "/warehouses/" + id, update, true).statusCode()).isEqualTo(200);
        assertThat(client.send("PUT", "/warehouses/" + id, update, true).statusCode()).isEqualTo(409);
        assertThat(json(client.send("GET", "/warehouses/" + id, null, false)).path("version").asLong()).isEqualTo(1);
        assertThat(json(client.send("GET", "/warehouses?q=hub&active=false&size=1", null, false)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(client.send("POST", "/warehouses", Map.of("code", "wh-1", "name", "Duplicate", "active", true), true).statusCode()).isEqualTo(409);
        assertThat(client.send("POST", "/warehouses", Map.of("code", "bad code", "name", " ", "active", true), true).statusCode()).isEqualTo(400);
        assertThat(client.send("GET", "/warehouses?size=101", null, false).statusCode()).isEqualTo(400);
    }

    @Test
    void receiptAtomicallyCreatesBalanceAndAuditableMovement() throws Exception {
        var client = signedIn(writer);
        var requestId = UUID.randomUUID();
        var response = client.send("POST", "/inventory/receipts", receipt(requestId, 25), true);
        assertThat(response.statusCode()).isEqualTo(201);
        var movement = json(response).path("movement");
        assertThat(movement.path("quantity").asLong()).isEqualTo(25);
        assertThat(movement.path("receivedBy").asString()).isEqualTo(actorId.toString());
        assertThat(movement.path("receivedByUsername").asString()).isEqualTo(writer);
        var id = movement.path("id").asString();
        assertThat(client.send("GET", "/inventory/movements/" + id, null, false).statusCode()).isEqualTo(200);
        var balance = json(client.send("GET", "/inventory?warehouseId=" + warehouse.id() + "&q=bolt", null, false)).path("items").get(0);
        assertThat(balance.path("onHand").asLong()).isEqualTo(25);
        assertThat(balance.path("reserved").asLong()).isZero();
        assertThat(balance.path("available").asLong()).isEqualTo(25);
        assertThat(json(client.send("GET", "/inventory/movements?q=DOC-1&size=1", null, false)).path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void exactRetriesReplayEvenAfterDeactivationAndChangedPayloadConflicts() throws Exception {
        var client = signedIn(writer); var requestId = UUID.randomUUID();
        var first = client.send("POST", "/inventory/receipts", receipt(requestId, 8), true);
        warehouses.update(organizationId, warehouse.id(), new WarehouseValues("WH-1", "Main test warehouse", null, false), 0);
        var replay = client.send("POST", "/inventory/receipts", receipt(requestId, 8), true);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(json(replay).path("replayed").asBoolean()).isTrue();
        assertThat(json(replay).path("movement").path("id").asString()).isEqualTo(json(first).path("movement").path("id").asString());
        assertThat(client.send("POST", "/inventory/receipts", receipt(requestId, 9), true).statusCode()).isEqualTo(409);
        assertThat(inventory.balances(organizationId, "", null, null, 0, 20).items().get(0).onHand()).isEqualTo(8);
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void quantityAndIdentifierValidationRejectInvalidRequestsWithoutSideEffects() throws Exception {
        var client = signedIn(writer);
        for (Object quantity : new Object[]{0, -1, 1.5, StockReceiptRequest.MAX_QUANTITY + 1}) {
            var input = new HashMap<>(receipt(UUID.randomUUID(), 1)); input.put("quantity", quantity);
            assertThat(client.send("POST", "/inventory/receipts", input, true).statusCode()).as("quantity %s", quantity).isEqualTo(400);
        }
        assertThat(client.send("POST", "/inventory/receipts", Map.of("quantity", 1), true).statusCode()).isEqualTo(400);
        assertThat(client.send("GET", "/inventory?warehouseId=invalid", null, false).statusCode()).isEqualTo(400);
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isZero();
    }

    @Test
    void inactiveWarehousesAndProductsRejectNewReceipts() throws Exception {
        var client = signedIn(writer);
        products.update(organizationId, product.id(), new ProductValues("BOLT-1", "Steel bolt", null, "Hardware", "Each", false), 0);
        assertThat(client.send("POST", "/inventory/receipts", receipt(UUID.randomUUID(), 1), true).statusCode()).isEqualTo(409);
        products.update(organizationId, product.id(), new ProductValues("BOLT-1", "Steel bolt", null, "Hardware", "Each", true), 1);
        warehouses.update(organizationId, warehouse.id(), new WarehouseValues("WH-1", "Main test warehouse", null, false), 0);
        assertThat(client.send("POST", "/inventory/receipts", receipt(UUID.randomUUID(), 1), true).statusCode()).isEqualTo(409);
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isZero();
    }

    @Test
    void tenantBoundaryIncludesWarehousesBalancesMovementsAndReceiptReferences() throws Exception {
        var movement = inventory.receive(organizationId, actorId, request(UUID.randomUUID(), 4)).movement();
        var other = signedIn(outsider);
        assertThat(other.send("GET", "/warehouses/" + warehouse.id(), null, false).statusCode()).isEqualTo(404);
        assertThat(other.send("PUT", "/warehouses/" + warehouse.id(), Map.of("code", "WH-1", "name", "Tampered", "active", true, "version", 0), true).statusCode()).isEqualTo(404);
        assertThat(other.send("GET", "/inventory/movements/" + movement.id(), null, false).statusCode()).isEqualTo(404);
        assertThat(json(other.send("GET", "/inventory?warehouseId=" + warehouse.id(), null, false)).path("totalElements").asLong()).isZero();
        assertThat(json(other.send("GET", "/inventory/movements", null, false)).path("totalElements").asLong()).isZero();
        assertThat(other.send("POST", "/inventory/receipts", receipt(UUID.randomUUID(), 1), true).statusCode()).isEqualTo(404);
        var own = signedIn(writer);
        var forged = new HashMap<>(receipt(UUID.randomUUID(), 1)); forged.put("productId", otherProduct.id());
        assertThat(own.send("POST", "/inventory/receipts", forged, true).statusCode()).isEqualTo(404);
        forged.put("productId", product.id()); forged.put("warehouseId", otherWarehouse.id());
        assertThat(own.send("POST", "/inventory/receipts", forged, true).statusCode()).isEqualTo(404);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tradesphere.inventory_balances (organization_id, warehouse_id, product_id) VALUES (?, ?, ?)",
                organizationId, warehouse.id(), otherProduct.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void inventoryPermissionIsIndependentFromCatalogAndCsrfIsRequired() throws Exception {
        assertThat(new ApiClient().send("GET", "/inventory", null, false).statusCode()).isEqualTo(401);
        for (var username : new String[]{viewer, catalogEditor}) {
            var client = signedIn(username);
            assertThat(client.send("GET", "/warehouses", null, false).statusCode()).isEqualTo(200);
            assertThat(client.send("POST", "/inventory/receipts", receipt(UUID.randomUUID(), 1), true).statusCode()).isEqualTo(403);
            assertThat(client.send("POST", "/warehouses", Map.of("code", "NEW", "name", "New", "active", true), true).statusCode()).isEqualTo(403);
        }
        var client = signedIn(writer);
        assertThat(client.send("POST", "/inventory/receipts", receipt(UUID.randomUUID(), 1), false).statusCode()).isEqualTo(403);
        assertThat(client.send("POST", "/products", Map.of("sku", "NEW", "name", "New", "category", "Test", "unitOfMeasure", "Each", "active", true), true).statusCode()).isEqualTo(403);
    }

    @Test
    void balanceFailureRollsBackAlreadyInsertedMovement() {
        inventory.receive(organizationId, actorId, request(UUID.randomUUID(), StockReceiptRequest.MAX_QUANTITY));
        assertThatThrownBy(() -> inventory.receive(organizationId, actorId, request(UUID.randomUUID(), 1))).isInstanceOf(InventoryException.class).hasMessageContaining("quantity range");
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isEqualTo(1);
        assertThat(inventory.balances(organizationId, "", null, null, 0, 20).items().get(0).onHand()).isEqualTo(StockReceiptRequest.MAX_QUANTITY);
    }

    @Test
    void databaseComputesAvailabilityAndRejectsInvalidReservations() {
        inventory.receive(organizationId, actorId, request(UUID.randomUUID(), 20));
        jdbc.update("UPDATE tradesphere.inventory_balances SET reserved = 6 WHERE organization_id = ?", organizationId);
        assertThat(inventory.balances(organizationId, "", null, null, 0, 20).items().get(0).available()).isEqualTo(14);
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.inventory_balances SET reserved = 21 WHERE organization_id = ?", organizationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.inventory_balances SET on_hand = -1 WHERE organization_id = ?", organizationId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentDistinctReceiptsDoNotLoseUpdates() throws Exception {
        concurrent(request(UUID.randomUUID(), 7), request(UUID.randomUUID(), 11));
        assertThat(inventory.balances(organizationId, "", null, null, 0, 20).items().get(0).onHand()).isEqualTo(18);
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    void concurrentSameRequestIsAppliedExactlyOnce() throws Exception {
        var request = request(UUID.randomUUID(), 7);
        concurrent(request, request);
        assertThat(inventory.balances(organizationId, "", null, null, 0, 20).items().get(0).onHand()).isEqualTo(7);
        assertThat(inventory.movements(organizationId, "", null, null, 0, 20).totalElements()).isEqualTo(1);
    }

    private void concurrent(StockReceiptRequest first, StockReceiptRequest second) throws Exception {
        var barrier = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.Stream.of(first, second).map(input -> executor.submit(() -> {
                if (!barrier.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                return inventory.receive(organizationId, actorId, input);
            })).toList();
            barrier.countDown(); for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        }
    }

    private StockReceiptRequest request(UUID id, long quantity) { return new StockReceiptRequest(id, warehouse.id(), product.id(), quantity, "DOC-1", null); }
    private Map<String, Object> receipt(UUID id, long quantity) { return Map.of("requestId", id, "warehouseId", warehouse.id(), "productId", product.id(), "quantity", quantity, "reference", "DOC-1"); }
    private static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private void user(UUID id, String username, UUID organization, String hash, boolean catalogWrite, boolean inventoryWrite) {
        jdbc.update("INSERT INTO tradesphere.users (id, organization_id, username, password_hash, can_write_catalog, can_write_inventory) VALUES (?, ?, ?, ?, ?, ?)", id, organization, username, hash, catalogWrite, inventoryWrite);
    }
    private ApiClient signedIn(String username) throws Exception { var client = new ApiClient(); client.login(username); return client; }

    private class ApiClient {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf; private String header;
        void token() throws Exception { var result = json(send("GET", "/auth/csrf", null, false)); csrf = result.path("token").asString(); header = result.path("headerName").asString(); }
        void login(String username) throws Exception {
            token();
            var body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            var response = http.send(HttpRequest.newBuilder(uri("/auth/login")).header(header, csrf).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(204); token();
        }
        HttpResponse<String> send(String method, String path, Object body, boolean secure) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10));
            if (secure) request.header(header, csrf);
            if (body != null) request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api/v1" + path); }
    }
}
