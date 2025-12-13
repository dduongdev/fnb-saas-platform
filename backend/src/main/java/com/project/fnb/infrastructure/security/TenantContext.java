package com.project.fnb.infrastructure.security;

import org.springframework.stereotype.Component;

/**
 * Lớp quản lý ngữ cảnh Tenant cho từng request trong môi trường multi-tenant.
 * 
 * <p>Class này sử dụng ThreadLocal để lưu trữ Tenant ID riêng biệt cho mỗi luồng (thread),
 * đảm bảo rằng dữ liệu Tenant của request này không bị ảnh hưởng bởi request khác.
 * ThreadLocal tự động yêu cầu từng thread có biến instance riêng, vì vậy không có rủi ro
 * về thread safety.</p>
 * 
 * <p>Đây là một thành phần Spring (Component) được sử dụng để lưu trữ và truy xuất
 * Tenant ID trong suốt vòng đời xử lý của một HTTP request.</p>
 * 
 * <p><b>Cách sử dụng:</b></p>
 * <ul>
 *   <li>Thiết lập Tenant ID: {@link #setTenantId(String)}</li>
 *   <li>Lấy Tenant ID: {@link #getTenantId()}</li>
 *   <li>Xóa sạch Tenant ID: {@link #clear()}</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 */
@Component
public class TenantContext {
    /**
     * ThreadLocal lưu trữ Tenant ID cho mỗi thread xử lý request.
     * Mỗi thread có giá trị riêng của biến này, đảm bảo thread safety
     * mà không cần sử dụng synchronized.
     */
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    /**
     * Thiết lập Tenant ID cho thread xử lý request hiện tại.
     * 
     * <p>Phương thức này được gọi bởi TenantFilter để lưu trữ Tenant ID
     * từ HTTP header vào ThreadLocal của thread hiện tại.</p>
     * 
     * @param tenantId ID của tenant, không được phép null hoặc rỗng
     */
    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }

    /**
     * Lấy Tenant ID của thread xử lý request hiện tại.
     * 
     * <p>Phương thức này có thể được gọi từ bất kỳ đâu trong request
     * để lấy Tenant ID của tenant hiện tại đang được xử lý.</p>
     * 
     * @return Tenant ID của request hiện tại, hoặc null nếu chưa được thiết lập
     */
    public static String getTenantId() {
        return currentTenant.get();
    }

    /**
     * Xóa sạch Tenant ID khỏi ThreadLocal của thread hiện tại.
     * 
     * <p>Phương thức này <b>PHẢI</b> được gọi sau khi xử lý xong request
     * để tránh rò rỉ bộ nhớ (memory leak) và ngăn chặn việc Tenant ID
     * của request này bị sử dụng nhầm cho request khác trong thread pool.</p>
     * 
     * <p>Thường được gọi trong block finally của TenantFilter để đảm bảo
     * luôn được thực thi.</p>
     */
    public static void clear() {
        currentTenant.remove();
    }
}