package com.tradesphere.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record CatalogUser(UUID id, UUID organizationId, String organizationName, String username,
                          String passwordHash, boolean canWriteCatalog, boolean enabled) implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return canWriteCatalog
                ? List.of(new SimpleGrantedAuthority("catalog:read"), new SimpleGrantedAuthority("catalog:write"))
                : List.of(new SimpleGrantedAuthority("catalog:read"));
    }

    @Override
    public String getUsername() { return username; }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() { return "CatalogUser[id=" + id + "]"; }
}
