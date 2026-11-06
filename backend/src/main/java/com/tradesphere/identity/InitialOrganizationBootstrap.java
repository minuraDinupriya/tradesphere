package com.tradesphere.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, one-time local setup; no public account-registration or administration API. */
@Component
@ConditionalOnProperty(name = "tradesphere.bootstrap.enabled", havingValue = "true")
public class InitialOrganizationBootstrap implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(InitialOrganizationBootstrap.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public InitialOrganizationBootstrap(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, Environment environment) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT count(*) FROM tradesphere.users", Map.of(), Long.class) > 0) {
            LOG.info("Initial account setup skipped: users already exist; no credentials were changed.");
            return;
        }
        var username = environment.getRequiredProperty("BOOTSTRAP_USERNAME").strip().toLowerCase(Locale.ROOT);
        var password = environment.getRequiredProperty("BOOTSTRAP_PASSWORD");
        var organization = environment.getRequiredProperty("BOOTSTRAP_ORGANIZATION_NAME").strip();
        if (!username.matches("[a-z0-9._-]{3,80}") || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || organization.isEmpty() || organization.length() > 160) {
            throw new IllegalArgumentException("Initial setup requires a 3–80 character username, a 12+ character password (max 72 UTF-8 bytes), and an organization name (max 160).");
        }
        var organizationId = UUID.randomUUID();
        jdbc.update("INSERT INTO tradesphere.organizations (id, name) VALUES (:id, :name)",
                Map.of("id", organizationId, "name", organization));
        jdbc.update("""
                INSERT INTO tradesphere.users (id, organization_id, username, password_hash, can_write_catalog, can_write_inventory, can_write_customers, can_write_prices, can_write_orders)
                VALUES (:id, :organizationId, :username, :hash, true, true, true, true, true)
                """, Map.of("id", UUID.randomUUID(), "organizationId", organizationId,
                "username", username, "hash", encoder.encode(password)));
        LOG.info("Initial organization and business editor created. Disable bootstrap and remove its password from local configuration.");
    }
}
