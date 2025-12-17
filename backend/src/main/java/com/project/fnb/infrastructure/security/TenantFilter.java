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

        // 1. Lấy URI của request
        String path = req.getRequestURI();

        // 2. Định nghĩa các URL không cần Tenant Context
        if (path.startsWith("/api/public/") || 
            path.startsWith("/api/auth/") ||
            path.startsWith("/api/recruitment/") || 
            path.startsWith("/api/profile/") || 
            path.startsWith("/api/tenants")
        ) {
            
            chain.doFilter(request, response);
            return; // Cho qua và không xử lý Tenant nữa
        }
        
        // --- LOGIC BẮT BUỘC ---
        // 3. Nếu là API nghiệp vụ, phải có Header
        String tenantId = req.getHeader(TENANT_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            // Ném lỗi ngay lập tức
            res.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
            res.setContentType("application/json");
            res.getWriter().write("{\"code\": 403, \"message\": \"Access Denied: Missing X-Tenant-ID header\"}");
            return; // Chặn request
        }

        // 4. Nếu hợp lệ, set Context và tiếp tục
        TenantContext.setTenantId(tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}