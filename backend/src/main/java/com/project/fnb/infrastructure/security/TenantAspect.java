package com.project.fnb.infrastructure.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import com.project.fnb.common.BaseEntity;

/**
 * Aspect để tự động kích hoạt Hibernate Filter cho tenant isolation trong các repository calls.
 * 
 * <p>Class này sử dụng AOP (Aspect-Oriented Programming) để tự động áp dụng tenant filtering
 * cho tất cả các phương thức repository, đảm bảo rằng mỗi request chỉ có thể truy cập dữ liệu
 * của tenant mà nó đang xử lý. Điều này là một lớp bảo vệ bổ sung ngoài @SQLRestriction
 * của BaseEntity.</p>
 * 
 * <p><b>Cơ chế hoạt động:</b></p>
 * <ul>
 *   <li>Bắt tất cả gọi method trong các repository classes</li>
 *   <li>Trước khi thực thi repository method, enableTenantFilter() được gọi</li>
 *   <li>Lấy tenant ID từ TenantContext (được set bởi TenantFilter)</li>
 *   <li>Nếu tenant ID tồn tại, kích hoạt Hibernate filter "tenantFilter" với tenant ID đó</li>
 *   <li>Việc filter này sẽ chặn các query không phù hợp với tenant hiện tại</li>
 * </ul>
 * 
 * <p><b>Lợi ích:</b></p>
 * <ul>
 *   <li><b>Automatic Tenant Filtering:</b> Không cần manually filter các queries</li>
 *   <li><b>Multiple Defense Layers:</b> Kết hợp với @SQLRestriction tạo thêm lớp bảo vệ</li>
 *   <li><b>Transparent to Business Logic:</b> Các repository không cần biết về tenant filtering</li>
 *   <li><b>Performance:</b> Filter được áp dụng ở mức database layer, hiệu quả</li>
 *   <li><b>Security:</b> Đảm bảo dữ liệu tenant A không thể truy cập bởi tenant B</li>
 * </ul>
 * 
 * <p><b>Luồng hoạt động chi tiết:</b></p>
 * <ol>
 *   <li>Request đến với X-Tenant-ID header</li>
 *   <li>TenantFilter trích xuất tenant ID từ header</li>
 *   <li>TenantContext.setTenantId(tenantId) được gọi</li>
 *   <li>Khi repository method được gọi, TenantAspect.enableTenantFilter() được kích hoạt</li>
 *   <li>Aspect kiểm tra TenantContext để lấy tenant ID</li>
 *   <li>Kích hoạt Hibernate filter với tenant ID</li>
 *   <li>Repository query được thực thi với filter đã kích hoạt</li>
 *   <li>Chỉ dữ liệu của tenant hiện tại mới được trả về</li>
 * </ol>
 * 
 * <p><b>Mối liên hệ với các thành phần khác:</b></p>
 * <ul>
 *   <li>{@link TenantFilter}: Trích xuất tenant ID từ HTTP header</li>
 *   <li>{@link TenantContext}: Lưu trữ tenant ID trong ThreadLocal</li>
 *   <li>{@link BaseEntity}: Định nghĩa @Filter("tenantFilter") trên entity</li>
 *   <li>Hibernate Filter: Mục đích tenant isolation ở database layer</li>
 * </ul>
 * 
 * <p><b>Lưu ý quan trọng:</b></p>
 * <ul>
 *   <li>Aspect này chỉ hoạt động nếu tenant ID tồn tại trong TenantContext</li>
 *   <li>Nếu TenantContext không có tenant ID, filter sẽ không được kích hoạt (có thể là vấn đề bảo mật)</li>
 *   <li>Pointcut "execution(* com.project.fnb.modules..repository..*(..))"
 *       chỉ áp dụng cho repositories trong modules package</li>
 *   <li>Các repository khác ngoài modules package sẽ không được filter</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see TenantFilter
 * @see TenantContext
 * @see BaseEntity
 */
@Aspect
@Component
@Slf4j
public class TenantAspect {

    /**
     * EntityManager được inject bởi Spring để truy cập Hibernate Session.
     * 
     * <p>@PersistenceContext annotation cho phép Spring tự động inject EntityManager.
     * EntityManager cung cấp quyền truy cập vào underlying Hibernate Session,
     * từ đó chúng ta có thể kích hoạt/tắt Hibernate filters.</p>
     * 
     * <p>Cách sử dụng:</p>
     * <pre>
     * {@code
     * Session session = entityManager.unwrap(Session.class);
     * session.enableFilter("filterName");
     * }
     * </pre>
     * 
     * <p><b>Lưu ý:</b> EntityManager là thread-safe và được quản lý bởi Spring,
     * mỗi request có một instance riêng của EntityManager.</p>
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Kích hoạt Hibernate Filter "tenantFilter" với tenant ID hiện tại.
     * 
     * <p>Phương thức này được gọi bởi AOP advice @Before trước khi bất kỳ repository method
     * nào trong packages com.project.fnb.modules.*.repository được thực thi.
     * Điều này đảm bảo rằng mỗi query sẽ tự động bị filter để chỉ trả về dữ liệu
     * của tenant hiện tại.</p>
     * 
     * <p><b>Pointcut chi tiết:</b></p>
     * <ul>
     *   <li>Pattern: "execution(* com.project.fnb.modules..repository..*(..))\"}</li>
     *   <li>Ý nghĩa: Tất cả phương thức (*) trong các class (.*) của packages
     *       com.project.fnb.modules.*.repository (.. = any subpackages)</li>
     *   <li>Ví dụ match:</li>
     *   <ul>
     *     <li>UserRepository.findById()</li>
     *     <li>ProductRepository.findByCategory()</li>
     *     <li>CategoryRepository.findAll()</li>
     *   </ul>
     * </ul>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Lấy tenant ID hiện tại từ TenantContext.getTenantId()</li>
     *   <li>Kiểm tra nếu tenant ID không null:</li>
     *   <ul>
     *     <li>Unwrap EntityManager để lấy Hibernate Session</li>
     *     <li>Gọi session.enableFilter("tenantFilter") để kích hoạt filter</li>
     *     <li>Thiết lập tenant ID vào filter parameter</li>
     *   </ul>
     *   <li>Nếu tenant ID là null, filter không được kích hoạt</li>
     *   <li>Repository method tiếp tục thực thi với filter đã kích hoạt</li>
     *   <li>Hibernate sẽ áp dụng filter vào tất cả SQL queries được tạo ra</li>
     * </ol>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     *   <li>Nếu TenantContext không có tenant ID (null), filter không được kích hoạt.
     *       Điều này có thể là vấn đề bảo mật nếu có request không set tenant ID.</li>
     *   <li>Phương thức này không throw exception, chỉ silent skip nếu tenant ID là null</li>
     *   <li>Pointcut chỉ apply cho packages under modules, không bao gồm global hoặc config packages</li>
     *   <li>Filter chỉ áp dụng cho entities trong session, không ảnh hưởng đến entities
     *       đã được load trước khi filter được kích hoạt</li>
     * </ul>
     * 
     * <p><b>Vấn đề tiềm ẩn:</b></p>
     * <ul>
     *   <li>Nếu tenant ID là null, sẽ không có filtering - có thể leak dữ liệu giữa tenants</li>
     *   <li>Khuyến khích thêm logic kiểm tra để throw exception nếu tenant ID không tồn tại</li>
     * </ul>
     * 
     * @see TenantContext#getTenantId()
     * @see TenantFilter
     * @see BaseEntity
     */
    @Before("execution(* com.project.fnb.modules..repository..*.*(..))")
    public void enableTenantFilter() {
        String tenantId = TenantContext.getTenantId();

        log.info("AOP Checking Tenant: {}", tenantId);

        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        }
    }
}