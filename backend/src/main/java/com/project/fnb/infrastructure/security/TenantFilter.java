package com.project.fnb.infrastructure.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class TenantFilter implements Filter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        String tenantId = req.getHeader(TENANT_HEADER);

        // Logic check whitelist
        boolean isWhitelisted = path.startsWith("/api/public/") ||
                path.startsWith("/api/auth/") ||
                path.startsWith("/api/recruitment/") ||
                path.startsWith("/api/profile/") ||
                path.startsWith("/api/tenants") ||
                path.startsWith("/api/pos/public/") || // Added this
                path.startsWith("/ws"); // WebSocket endpoint

        // 1. Nếu có tenantId hợp lệ -> luôn set context
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
            try {
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        // 2. Không có tenantId -> Check whitelist
        if (isWhitelisted) {
            chain.doFilter(request, response);
            return;
        }

        // 3. Không có tenantId và không phải whitelist -> Lỗi
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().write("{\"code\": 403, \"message\": \"Access Denied: Missing X-Tenant-ID header\"}");
    }
}