package com.easyeat.common.filter;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.security.JwtService;
import com.easyeat.common.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that extracts the tenant identity for the current request and populates {@link TenantContextHolder}.
 * Derives tenant_id from the authenticated principal or verified JWT claims, and always cleans up in a finally block.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public TenantContextFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        UUID tenantId = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            tenantId = user.getTenantId();
        }

        if (tenantId == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    tenantId = jwtService.extractTenantId(token);
                } catch (Exception ignored) {
                    // Invalid token or malformed claim: tenant remains unset
                }
            }
        }

        if (tenantId != null) {
            TenantContextHolder.setTenantId(tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
