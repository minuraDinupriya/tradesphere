package com.tradesphere.identity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationUserDetailsService implements UserDetailsService {
    private final NamedParameterJdbcTemplate jdbc;

    public OrganizationUserDetailsService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var users = jdbc.query("""
                SELECT u.*, o.name AS organization_name
                FROM tradesphere.users u JOIN tradesphere.organizations o ON o.id = u.organization_id
                WHERE lower(u.username) = :username
                """, Map.of("username", username.strip().toLowerCase(Locale.ROOT)), (rs, row) ->
                new OrganizationUser(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                        rs.getString("organization_name"), rs.getString("username"), rs.getString("password_hash"),
                        rs.getBoolean("can_write_catalog"), rs.getBoolean("can_write_inventory"),
                        rs.getBoolean("can_write_customers"), rs.getBoolean("can_write_prices"), rs.getBoolean("enabled")));
        return users.stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
