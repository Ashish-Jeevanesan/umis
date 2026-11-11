package com.po.processor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter intercepting incoming HTTP requests to validate JWT bearer tokens and populate SecurityContext.
 */
@Component
public class PoJwtAuthenticationFilter extends OncePerRequestFilter {

    private final PoJwtTokenProvider tokenProvider;

    public PoJwtAuthenticationFilter(PoJwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String strToken = extractBearerToken(request);

        if (StringUtils.hasText(strToken) && tokenProvider.validateToken(strToken)) {
            PoUserPrincipal principal = tokenProvider.parsePrincipal(strToken);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String strAuthHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(strAuthHeader) && strAuthHeader.startsWith("Bearer ")) {
            return strAuthHeader.substring(7).trim();
        }
        return null;
    }
}
