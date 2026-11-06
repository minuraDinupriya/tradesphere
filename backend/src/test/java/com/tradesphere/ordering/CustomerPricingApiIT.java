package com.tradesphere.ordering;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import com.tradesphere.catalog.ProductConflictException;
import com.tradesphere.catalog.SellingPriceService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tradesphere.bootstrap.enabled=false")
class CustomerPricingApiIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private CustomerService customers;
    @Autowired private ProductService products;
    @Autowired private SellingPriceService prices;
    private UUID organizationId;
    private UUID otherOrganizationId;
    private String writer;
    private String viewer;
    private String legacyEditor;
    private String outsider;
    private String password;
    private Product product;
    private Customer customer;
    private Customer foreignCustomer;

    @BeforeEach
    void fixtures() {
        organizationId = UUID.randomUUID(); otherOrganizationId = UUID.randomUUID();
        writer = "sales-" + UUID.randomUUID(); viewer = "viewer-" + UUID.randomUUID();
        legacyEditor = "legacy-" + UUID.randomUUID(); outsider = "other-" + UUID.randomUUID(); password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tradesphere.organizations (id, name) VALUES (?, ?), (?, ?)", organizationId, "Fictional Sales Test Organization", otherOrganizationId, "Other Fictional Organization");
        var hash = encoder.encode(password);
        user(writer, organizationId, hash, false, true, true);
        user(viewer, organizationId, hash, false, false, false);
        // Omitting the new columns exercises migration defaults for existing provisioning paths.
        jdbc.update("INSERT INTO tradesphere.users (id, organization_id, username, password_hash, can_write_catalog, can_write_inventory) VALUES (?, ?, ?, ?, true, true)", UUID.randomUUID(), organizationId, legacyEditor, hash);
        user(outsider, otherOrganizationId, hash, false, true, true);
        product = products.create(organizationId, new ProductValues("BOLT-1", "Test bolt", null, "Hardware", "Each", true));
        customer = customers.create(organizationId, values("Main test customer", true));
        foreignCustomer = customers.create(otherOrganizationId, values("Private test customer", true));
    }

    @AfterEach
    void cleanOwnFixtures() {
        for (var table : new String[]{"customers", "products", "users", "organizations"}) {
            var column = table.equals("organizations") ? "id" : "organization_id";
            jdbc.update("DELETE FROM tradesphere." + table + " WHERE " + column + " IN (?, ?)", organizationId, otherOrganizationId);
        }
    }

    @Test
    void customerCreateReadEditSearchAndStatusWorkWithPersistedNormalization() throws Exception {
        var client = signedIn(writer);
        var response = client.send("POST", "/customers", Map.of("name", "  Colombo test hardware  ", "contactName", " Test Contact ", "email", "fictional@example.test", "phone", "+94 11 000 0000", "address", "Fictional address", "active", true), true);
        assertThat(response.statusCode()).isEqualTo(201);
        var created = json(response);
        assertThat(created.path("name").asString()).isEqualTo("Colombo test hardware");
        assertThat(created.path("contactName").asString()).isEqualTo("Test Contact");
        var id = created.path("id").asString();
        assertThat(client.send("GET", "/customers/" + id, null, false).statusCode()).isEqualTo(200);
        var edit = Map.of("name", "Updated hardware", "active", false, "version", 0);
        assertThat(client.send("PUT", "/customers/" + id, edit, true).statusCode()).isEqualTo(200);
        assertThat(client.send("PUT", "/customers/" + id, edit, true).statusCode()).isEqualTo(409);
        var page = json(client.send("GET", "/customers?q=Updated&active=false&size=1", null, false));
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("version").asLong()).isEqualTo(1);
        assertThat(client.send("POST", "/customers", Map.of("name", "Updated hardware", "active", true), true).statusCode()).isEqualTo(201);
    }

    @Test
    void customerValidationAndLiteralWildcardSearchDoNotLeakAllRows() throws Exception {
        var client = signedIn(writer);
        for (var body : new Map[]{Map.of("name", " ", "active", true), Map.of("name", "x".repeat(161), "active", true), Map.of("name", "Test", "email", "invalid", "active", true), Map.of("name", "Test", "phone", "1".repeat(41), "active", true), Map.of("name", "Test")}) {
            assertThat(client.send("POST", "/customers", body, true).statusCode()).isEqualTo(400);
        }
        assertThat(client.send("GET", "/customers?page=-1", null, false).statusCode()).isEqualTo(400);
        assertThat(client.send("GET", "/customers?size=101", null, false).statusCode()).isEqualTo(400);
        customers.create(organizationId, values("100% hardware", true));
        assertThat(json(client.send("GET", "/customers?q=%25", null, false)).path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void unsetZeroFractionalAndMaximumPricesPersistExactly() throws Exception {
        var client = signedIn(writer);
        assertThat(json(client.send("GET", pricePath(), null, false)).path("amount").isNull()).isTrue();
        long version = 0;
        for (var amount : new String[]{"0", "0.10", "1234.56", "999999999999.99"}) {
            var response = client.send("PUT", pricePath(), Map.of("amount", amount, "version", version++), true);
            assertThat(response.statusCode()).isEqualTo(200);
            var expected = new java.math.BigDecimal(amount).setScale(2).toPlainString();
            assertThat(json(response).path("amount").asString()).isEqualTo(expected);
            assertThat(json(response).path("currency").asString()).isEqualTo("LKR");
            assertThat(jdbc.queryForObject("SELECT selling_price FROM tradesphere.products WHERE id = ?", java.math.BigDecimal.class, product.id()).toPlainString()).isEqualTo(expected);
            assertThat(json(client.send("GET", "/products/" + product.id(), null, false)).path("sellingPrice").asString()).isEqualTo(expected);
        }
    }

    @Test
    void invalidPricesAndMissingVersionsAreRejectedWithoutChangingStoredPrice() throws Exception {
        var client = signedIn(writer);
        for (var amount : new String[]{"-1", "0.001", "1000000000000", "NaN", "Infinity", "1e2", "", "1,000.00"}) {
            assertThat(client.send("PUT", pricePath(), Map.of("amount", amount, "version", 0), true).statusCode()).as(amount).isEqualTo(400);
        }
        assertThat(client.send("PUT", pricePath(), Map.of("amount", "1.00"), true).statusCode()).isEqualTo(400);
        assertThat(client.send("PUT", pricePath(), Map.of("amount", 1.25, "version", 0), true).statusCode()).isEqualTo(400);
        assertThat(client.send("PUT", pricePath(), Map.of("amount", "1.00", "version", 0.5), true).statusCode()).isEqualTo(400);
        assertThat(prices.get(organizationId, product.id()).amount()).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.products SET selling_price = -0.01 WHERE id = ?", product.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.products SET selling_price = 'NaN'::numeric WHERE id = ?", product.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void catalogEditsCannotSetOrOverwriteSellingPrice() throws Exception {
        var client = signedIn(legacyEditor);
        prices.update(organizationId, product.id(), "42.15", 0);
        var payload = Map.of("sku", "BOLT-1", "name", "Edited test bolt", "category", "Hardware", "unitOfMeasure", "Each", "active", true, "version", 0, "sellingPrice", "0.01");
        assertThat(client.send("PUT", "/products/" + product.id(), payload, true).statusCode()).isEqualTo(200);
        assertThat(prices.get(organizationId, product.id()).amount()).isEqualTo("42.15");
        assertThat(client.send("PUT", pricePath(), Map.of("amount", "0.01", "version", 1), true).statusCode()).isEqualTo(403);
    }

    @Test
    void permissionsAreIndependentAndCsrfAndSignInRemainRequired() throws Exception {
        assertThat(new ApiClient().send("GET", "/customers", null, false).statusCode()).isEqualTo(401);
        assertThat(new ApiClient().send("GET", pricePath(), null, false).statusCode()).isEqualTo(401);
        for (var username : new String[]{viewer, legacyEditor}) {
            var client = signedIn(username);
            assertThat(client.send("GET", "/customers", null, false).statusCode()).isEqualTo(200);
            assertThat(client.send("POST", "/customers", Map.of("name", "Denied", "active", true), true).statusCode()).isEqualTo(403);
            assertThat(client.send("PUT", pricePath(), Map.of("amount", "1.00", "version", 0), true).statusCode()).isEqualTo(403);
        }
        var client = signedIn(writer);
        assertThat(client.send("POST", "/customers", Map.of("name", "Denied", "active", true), false).statusCode()).isEqualTo(403);
        assertThat(client.send("PUT", pricePath(), Map.of("amount", "1.00", "version", 0), false).statusCode()).isEqualTo(403);
        assertThat(client.send("POST", "/products", Map.of("sku", "NO", "name", "Denied", "category", "Test", "unitOfMeasure", "Each", "active", true), true).statusCode()).isEqualTo(403);
    }

    @Test
    void knownForeignIdentifiersAndSearchCannotCrossOrganizations() throws Exception {
        var foreign = signedIn(outsider);
        assertThat(foreign.send("GET", "/customers/" + customer.id(), null, false).statusCode()).isEqualTo(404);
        assertThat(foreign.send("PUT", "/customers/" + customer.id(), Map.of("name", "Tampered", "active", true, "version", 0), true).statusCode()).isEqualTo(404);
        assertThat(json(foreign.send("GET", "/customers?q=Main", null, false)).path("totalElements").asLong()).isZero();
        assertThat(foreign.send("GET", pricePath(), null, false).statusCode()).isEqualTo(404);
        assertThat(foreign.send("PUT", pricePath(), Map.of("amount", "1.00", "version", 0), true).statusCode()).isEqualTo(404);
        assertThat(signedIn(writer).send("GET", "/customers/" + foreignCustomer.id(), null, false).statusCode()).isEqualTo(404);
        assertThat(prices.get(organizationId, product.id()).amount()).isNull();
    }

    @Test
    void customerAndPriceWritePermissionsDoNotImplyEachOther() throws Exception {
        var customerWriter = "customer-only-" + UUID.randomUUID();
        var priceWriter = "price-only-" + UUID.randomUUID();
        var hash = encoder.encode(password);
        user(customerWriter, organizationId, hash, false, true, false);
        user(priceWriter, organizationId, hash, false, false, true);
        var customerClient = signedIn(customerWriter);
        assertThat(customerClient.send("POST", "/customers", Map.of("name", "Allowed", "active", true), true).statusCode()).isEqualTo(201);
        assertThat(customerClient.send("PUT", pricePath(), Map.of("amount", "1.00", "version", 0), true).statusCode()).isEqualTo(403);
        var priceClient = signedIn(priceWriter);
        assertThat(priceClient.send("POST", "/customers", Map.of("name", "Denied", "active", true), true).statusCode()).isEqualTo(403);
        assertThat(priceClient.send("PUT", pricePath(), Map.of("amount", "1.00", "version", 0), true).statusCode()).isEqualTo(200);
    }

    @Test
    void simultaneousPriceEditsHaveOneWinnerAndDoNotLoseUpdates() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.Stream.of("10.10", "20.20").map(amount -> executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { prices.update(organizationId, product.id(), amount, 0); return true; }
                catch (ProductConflictException conflict) { return false; }
            })).toList();
            start.countDown();
            var wins = 0;
            for (var task : tasks) if (task.get(15, TimeUnit.SECONDS)) wins++;
            assertThat(wins).isEqualTo(1);
        }
        var price = prices.get(organizationId, product.id());
        assertThat(price.version()).isEqualTo(1);
        assertThat(price.amount()).isIn("10.10", "20.20");
    }

    private static CustomerValues values(String name, boolean active) { return new CustomerValues(name, null, null, null, null, active); }
    private String pricePath() { return "/products/" + product.id() + "/selling-price"; }
    private static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private ApiClient signedIn(String username) throws Exception { var client = new ApiClient(); client.login(username); return client; }
    private void user(String username, UUID organization, String hash, boolean catalog, boolean customerWrite, boolean priceWrite) {
        jdbc.update("INSERT INTO tradesphere.users (id, organization_id, username, password_hash, can_write_catalog, can_write_customers, can_write_prices) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), organization, username, hash, catalog, customerWrite, priceWrite);
    }

    private class ApiClient {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        private String csrf;
        private String header;
        void token() throws Exception { var body = json(send("GET", "/auth/csrf", null, false)); csrf = body.path("token").asString(); header = body.path("headerName").asString(); }
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
