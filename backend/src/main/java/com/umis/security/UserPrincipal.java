package com.umis.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Custom UserDetails implementation holding authenticated user and tenant identity.
 */
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean active;

    public UserPrincipal(
            UUID userId,
            UUID tenantId,
            String email,
            String password,
            String role,
            boolean active
    ) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.password = password;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        this.active = active;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
