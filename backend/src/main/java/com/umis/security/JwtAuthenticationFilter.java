package com.umis.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that authenticates requests using Bearer JWT tokens and sets the TenantContext.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = resolveToken(request);
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Claims claims = tokenProvider.getClaimsFromToken(jwt);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                String userIdStr = claims.get("userId", String.class);
                String tenantIdStr = claims.get("tenantId", String.class);

                UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;
                UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;

                // Support Global Admin impersonating or operating in a tenant's context via X-Tenant-Id header
                String headerTenantId = request.getHeader("X-Tenant-Id");
                if (RoleConstants.GLOBAL_ADMIN.equals(role) && StringUtils.hasText(headerTenantId)) {
                    try {
                        tenantId = UUID.fromString(headerTenantId);
                    } catch (IllegalArgumentException ignored) {
                        // Fall back to original tenantId
                    }
                }

                UserPrincipal principal = new UserPrincipal(
                        userId,
                        tenantId,
                        email,
                        "",
                        role != null ? role : RoleConstants.TENANT_VIEWER,
                        true
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                    MDC.put("tenantId", tenantId.toString());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
