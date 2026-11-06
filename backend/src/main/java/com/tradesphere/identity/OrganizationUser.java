package com.tradesphere.identity;

import java.util.Collection;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record OrganizationUser(UUID id, UUID organizationId, String organizationName, String username,
                          String passwordHash, boolean canWriteCatalog, boolean canWriteInventory, boolean enabled) implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        var permissions = new ArrayList<GrantedAuthority>();
        permissions.add(new SimpleGrantedAuthority("catalog:read"));
        permissions.add(new SimpleGrantedAuthority("inventory:read"));
        if (canWriteCatalog) permissions.add(new SimpleGrantedAuthority("catalog:write"));
        if (canWriteInventory) permissions.add(new SimpleGrantedAuthority("inventory:write"));
        return permissions;
    }

    @Override
    public String getUsername() { return username; }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public String toString() { return "OrganizationUser[id=" + id + "]"; }
}
