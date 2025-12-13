package com.project.fnb.infrastructure.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Bộ lọc (Filter) để xử lý thông tin Tenant từ HTTP request headers.
 * 
 * <p>Class này chịu trách nhiệm trích xuất Tenant ID từ header "X-Tenant-ID"
 * trong mỗi HTTP request và thiết lập nó vào TenantContext để sử dụng trong
 * toàn bộ xử lý của request đó. Sau khi request được xử lý xong, TenantContext
 * sẽ được làm sạch để tránh rò rỉ dữ liệu giữa các request khác nhau.</p>
 * 
 * <p>Đây là một phần của chiến lược multi-tenancy, cho phép một instance ứng dụng
 * phục vụ nhiều khách hàng (tenant) khác nhau.</p>
 * 
 * @author Project Team
 * @version 1.0
 */
@Component
public class TenantFilter implements Filter {

    /**
     * Tên của HTTP header sử dụng để truyền Tenant ID.
     * Giá trị: "X-Tenant-ID"
     */
    private static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * Thực hiện lọc HTTP request để trích xuất và thiết lập Tenant ID.
     * 
     * <p>Phương thức này:</p>
     * <ul>
     *   <li>Lấy giá trị Tenant ID từ header "X-Tenant-ID"</li>
     *   <li>Nếu Tenant ID không rỗng, thiết lập nó vào TenantContext</li>
     *   <li>Truyền request đến filter tiếp theo trong chuỗi</li>
     *   <li>Xóa sạch TenantContext sau khi xử lý xong (trong block finally)</li>
     * </ul>
     * 
     * @param request  {@link ServletRequest} - đối tượng request HTTP
     * @param response {@link ServletResponse} - đối tượng response HTTP
     * @param chain    {@link FilterChain} - chuỗi các filter để tiếp tục xử lý
     * 
     * @throws IOException      nếu xảy ra lỗi I/O trong quá trình xử lý
     * @throws ServletException nếu xảy ra lỗi Servlet trong quá trình xử lý
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;

        String tenantId = req.getHeader(TENANT_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}