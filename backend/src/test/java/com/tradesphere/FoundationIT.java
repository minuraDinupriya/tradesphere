package com.tradesphere;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against PostgreSQL configured by the same environment as the application. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void healthReportsUpWithoutExposingDatabaseDetails() throws Exception {
        var response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"UP\"")
                .doesNotContain("\"components\"", "\"details\"", "\"db\"");
    }

    @Test
    void migrationsCreateNamespaceAndRecordSuccessfulVersion() {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'tradesphere'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void configurationEndpointsAreNotExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/configprops").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
