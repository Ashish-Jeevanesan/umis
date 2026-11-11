package com.po.processor.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Custom UserDetails implementation holding verified user and tenant claims from JWT.
 */
public class PoUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    public PoUserPrincipal(UUID userId, UUID tenantId, String email, List<String> alRoles) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;

        List<SimpleGrantedAuthority> alAuths = new ArrayList<>();
        if (alRoles != null) {
            for (String strRole : alRoles) {
                alAuths.add(new SimpleGrantedAuthority("ROLE_" + strRole));
            }
        }
        this.authorities = alAuths;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public boolean isGlobalAdmin() {
        return hasRole(PoRoleConstants.GLOBAL_ADMIN);
    }

    public boolean hasRole(String strRole) {
        String strExpected = "ROLE_" + strRole;
        return authorities.stream().anyMatch(auth -> auth.getAuthority().equals(strExpected));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
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
        return true;
    }
}
