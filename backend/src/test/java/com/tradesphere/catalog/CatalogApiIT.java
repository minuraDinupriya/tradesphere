package com.tradesphere.catalog;

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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tradesphere.bootstrap.enabled=false")
class CatalogApiIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ProductService products;

    private UUID firstOrganization;
    private UUID secondOrganization;
    private String writerName;
    private String viewerName;
    private String otherName;
    private String password;

    @BeforeEach
    void fixtures() {
        firstOrganization = UUID.randomUUID();
        secondOrganization = UUID.randomUUID();
        password = UUID.randomUUID().toString(); // Disposable generated fixture, not a committed credential.
        writerName = "writer-" + UUID.randomUUID();
        viewerName = "viewer-" + UUID.randomUUID();
        otherName = "other-" + UUID.randomUUID();
        jdbc.update("INSERT INTO tradesphere.organizations (id, name) VALUES (?, ?), (?, ?)",
                firstOrganization, "Fictional Test Distributor", secondOrganization, "Other Fictional Organization");
        var hash = encoder.encode(password);
        user(writerName, firstOrganization, hash, true);
        user(viewerName, firstOrganization, hash, false);
        user(otherName, secondOrganization, hash, true);
    }

    @AfterEach
    void cleanOwnFixtures() {
        jdbc.update("DELETE FROM tradesphere.products WHERE organization_id IN (?, ?)", firstOrganization, secondOrganization);
        jdbc.update("DELETE FROM tradesphere.users WHERE organization_id IN (?, ?)", firstOrganization, secondOrganization);
        jdbc.update("DELETE FROM tradesphere.organizations WHERE id IN (?, ?)", firstOrganization, secondOrganization);
    }

    @Test
    void authenticatedProductLifecyclePersistsAndProtectsStaleEdits() throws Exception {
        var client = signedIn(writerName);
        var response = client.send("POST", "/products", body("bolt-01", " Steel bolt ", true), true);
        assertThat(response.statusCode()).isEqualTo(201);
        var created = json(response);
        var id = created.path("id").asString();
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/products/" + id);
        assertThat(created.path("sku").asString()).isEqualTo("BOLT-01");
        assertThat(created.path("name").asString()).isEqualTo("Steel bolt");
        assertThat(created.has("organizationId")).isFalse();
        assertThat(jdbc.queryForObject("SELECT organization_id FROM tradesphere.products WHERE id = ?", UUID.class, UUID.fromString(id)))
                .isEqualTo(firstOrganization);
        var update = new HashMap<>(body("BOLT-01", "Updated bolt", false));
        update.put("version", 0);
        assertThat(client.send("PUT", "/products/" + id, update, true).statusCode()).isEqualTo(200);
        var stale = client.send("PUT", "/products/" + id, update, true);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(json(stale).path("detail").asString()).contains("changed");
        var retrieved = json(client.send("GET", "/products/" + id, null, false));
        assertThat(retrieved.path("name").asString()).isEqualTo("Updated bolt");
        assertThat(retrieved.path("active").asBoolean()).isFalse();
        assertThat(retrieved.path("version").asLong()).isEqualTo(1);
    }

    @Test
    void listSearchFiltersAndPaginationStayInsideAuthenticatedOrganization() throws Exception {
        products.create(firstOrganization, values("A-01", "Alpha bolt", "Hardware", true));
        products.create(firstOrganization, values("B-01", "Beta bolt", "Hardware", false));
        products.create(secondOrganization, values("SECRET", "Secret bolt", "Secret category", true));
        var client = signedIn(writerName);
        var first = json(client.send("GET", "/products?q=BOLT&page=0&size=1&organizationId=" + secondOrganization, null, false));
        assertThat(first.path("totalElements").asLong()).isEqualTo(2);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(first.path("items").get(0).path("name").asString()).isEqualTo("Alpha bolt");
        var next = json(client.send("GET", "/products?q=bolt&page=1&size=1", null, false));
        assertThat(next.path("items").get(0).path("name").asString()).isEqualTo("Beta bolt");
        var filtered = json(client.send("GET", "/products?category=hardware&active=false", null, false));
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("sku").asString()).isEqualTo("B-01");
        assertThat(client.send("GET", "/products/categories", null, false).body()).doesNotContain("Secret category");
        assertThat(json(client.send("GET", "/products?page=99&size=1", null, false)).path("items").size()).isZero();
    }

    @Test
    void literalSearchWildcardsDoNotAccidentallyMatchEveryProduct() throws Exception {
        products.create(firstOrganization, values("W-100", "100% washers", "Hardware", true));
        products.create(firstOrganization, values("B-100", "Plain bolts", "Hardware", true));
        var client = signedIn(writerName);
        assertThat(json(client.send("GET", "/products?q=%25", null, false)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(json(client.send("GET", "/products?q=_", null, false)).path("totalElements").asLong()).isZero();
        assertThat(json(client.send("GET", "/products?q=" + URLEncoder.encode("' OR 1=1 --", StandardCharsets.UTF_8), null, false))
                .path("totalElements").asLong()).isZero();
    }

    @Test
    void organizationBoundaryCoversDetailUpdateAndSameSkuInDifferentOrganizations() throws Exception {
        var product = products.create(firstOrganization, values("SAME-01", "First product", "Hardware", true));
        var other = signedIn(otherName);
        assertThat(other.send("GET", "/products/" + product.id(), null, false).statusCode()).isEqualTo(404);
        var update = new HashMap<>(body("SAME-01", "Tampered", true));
        update.put("version", 0);
        assertThat(other.send("PUT", "/products/" + product.id(), update, true).statusCode()).isEqualTo(404);
        var forged = new HashMap<>(body("SAME-01", "Other product", true));
        forged.put("organizationId", firstOrganization.toString());
        assertThat(other.send("POST", "/products", forged, true).statusCode()).isEqualTo(201);
        assertThat(products.get(firstOrganization, product.id()).name()).isEqualTo("First product");
        assertThat(products.list(secondOrganization, "", "", null, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void validationRejectsMissingOversizedMalformedAndOutOfRangeInputWithoutWrites() throws Exception {
        var client = signedIn(writerName);
        var invalid = new HashMap<>(body("bad sku", " ", true));
        invalid.put("description", "x".repeat(2001));
        invalid.put("active", null);
        var response = client.send("POST", "/products", invalid, true);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(json(response).path("errors").has("sku")).isTrue();
        assertThat(json(response).path("errors").has("name")).isTrue();
        assertThat(json(response).path("errors").has("description")).isTrue();
        assertThat(json(response).path("errors").has("active")).isTrue();
        for (var path : new String[]{"/products?page=-1", "/products?size=0", "/products?size=101", "/products?active=invalid", "/products/not-a-uuid"}) {
            assertThat(client.send("GET", path, null, false).statusCode()).as(path).isEqualTo(400);
        }
        assertThat(products.list(firstOrganization, "", "", null, 0, 20).totalElements()).isZero();
    }

    @Test
    void lengthBoundariesAndCaseInsensitiveSkuConflictAreEnforced() throws Exception {
        var client = signedIn(writerName);
        var boundary = new HashMap<>(body("X".repeat(64), "N".repeat(160), true));
        boundary.put("category", "C".repeat(80));
        boundary.put("unitOfMeasure", "U".repeat(32));
        boundary.put("description", "D".repeat(2000));
        assertThat(client.send("POST", "/products", boundary, true).statusCode()).isEqualTo(201);
        boundary.put("sku", "x".repeat(64));
        assertThat(client.send("POST", "/products", boundary, true).statusCode()).isEqualTo(409);
        boundary.put("sku", "X".repeat(65));
        assertThat(client.send("POST", "/products", boundary, true).statusCode()).isEqualTo(400);
    }

    @Test
    void anonymousReadOnlyAndCsrfBoundariesAreEnforcedByBackend() throws Exception {
        var anonymous = new ApiClient();
        assertThat(anonymous.send("GET", "/products", null, false).statusCode()).isEqualTo(401);
        assertThat(anonymous.login(writerName, "incorrect-" + password).statusCode()).isEqualTo(401);
        var viewer = signedIn(viewerName);
        assertThat(viewer.send("GET", "/products", null, false).statusCode()).isEqualTo(200);
        assertThat(viewer.send("POST", "/products", body("B-1", "Bolt", true), true).statusCode()).isEqualTo(403);
        var writer = signedIn(writerName);
        assertThat(writer.send("POST", "/products", body("B-1", "Bolt", true), false).statusCode()).isEqualTo(403);
        var product = products.create(firstOrganization, values("B-1", "Bolt", "Hardware", true));
        var update = new HashMap<>(body("B-1", "Changed", true)); update.put("version", 0);
        assertThat(viewer.send("PUT", "/products/" + product.id(), update, true).statusCode()).isEqualTo(403);
        assertThat(writer.send("POST", "/auth/logout", null, true).statusCode()).isEqualTo(204);
        assertThat(writer.send("GET", "/products", null, false).statusCode()).isEqualTo(401);
    }

    @Test
    void competingEditsCommitExactlyOneVersion() throws Exception {
        var product = products.create(firstOrganization, values("B-1", "Bolt", "Hardware", true));
        var successes = new AtomicInteger(); var conflicts = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> updateAfter(start, product, "First edit", successes, conflicts));
            var second = executor.submit(() -> updateAfter(start, product, "Second edit", successes, conflicts));
            start.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(1); assertThat(conflicts.get()).isEqualTo(1);
        var saved = products.get(firstOrganization, product.id());
        assertThat(saved.version()).isEqualTo(1); assertThat(saved.name()).isIn("First edit", "Second edit");
    }

    @Test
    void concurrentDuplicateSkuCreationIsRejectedByPostgreSql() throws Exception {
        var successes = new AtomicInteger(); var conflicts = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                await(start);
                try { products.create(firstOrganization, values("DUP-1", "Bolt", "Hardware", true)); successes.incrementAndGet(); }
                catch (ProductConflictException expected) { conflicts.incrementAndGet(); }
            })).toList();
            start.countDown(); for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(1); assertThat(conflicts.get()).isEqualTo(1);
        assertThat(products.list(firstOrganization, "", "", null, 0, 20).totalElements()).isEqualTo(1);
    }

    private void updateAfter(CountDownLatch start, Product product, String name, AtomicInteger successes, AtomicInteger conflicts) {
        await(start);
        try { products.update(firstOrganization, product.id(), values(product.sku(), name, "Hardware", true), 0); successes.incrementAndGet(); }
        catch (ProductConflictException expected) { conflicts.incrementAndGet(); }
    }

    private static void await(CountDownLatch start) {
        try { if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    private void user(String username, UUID organization, String hash, boolean write) {
        jdbc.update("INSERT INTO tradesphere.users (id, organization_id, username, password_hash, can_write_catalog) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), organization, username, hash, write);
    }

    private ApiClient signedIn(String username) throws Exception {
        var client = new ApiClient();
        assertThat(client.login(username, password).statusCode()).isEqualTo(204);
        return client;
    }

    private static ProductValues values(String sku, String name, String category, boolean active) {
        return new ProductValues(sku, name, null, category, "Each", active);
    }

    private static Map<String, Object> body(String sku, String name, boolean active) {
        return Map.of("sku", sku, "name", name, "description", "Test product", "category", "Hardware", "unitOfMeasure", "Each", "active", active);
    }

    private static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }

    private class ApiClient {
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        private String csrf;
        private String csrfHeader;

        HttpResponse<String> login(String username, String passwordValue) throws Exception {
            refreshCsrf();
            var body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(passwordValue, StandardCharsets.UTF_8);
            var response = http.send(HttpRequest.newBuilder(uri("/auth/login")).timeout(Duration.ofSeconds(10))
                    .header(csrfHeader, csrf).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) refreshCsrf();
            return response;
        }

        void refreshCsrf() throws Exception {
            var token = json(send("GET", "/auth/csrf", null, false));
            csrf = token.path("token").asString(); csrfHeader = token.path("headerName").asString();
        }

        HttpResponse<String> send(String method, String path, Object body, boolean secure) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10));
            if (secure) request.header(csrfHeader, csrf);
            if (body != null) request.header("Content-Type", "application/json");
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api/v1" + path); }
    }
}
