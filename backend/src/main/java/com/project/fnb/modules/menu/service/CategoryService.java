package com.project.fnb.modules.menu.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.menu.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dịch vụ quản lý danh mục (Category) trong hệ thống menu.
 * 
 * <p>Class này cung cấp các chức năng liên quan đến quản lý danh mục sản phẩm/thực đơn,
 * bao gồm tạo, cập nhật, xóa và truy vấn danh mục. Mỗi danh mục được liên kết với
 * một tenant cụ thể thông qua TenantContext.</p>
 * 
 * <p><b>Tính năng chính:</b></p>
 * <ul>
 *   <li>Tạo danh mục mặc định cho tenant mới</li>
 *   <li>Tự động ghi nhận tenant ID cho mỗi danh mục</li>
 *   <li>Quản lý danh mục theo tenant (multi-tenancy)</li>
 * </ul>
 * 
 * <p><b>Lưu ý về TenantContext:</b><br>
 * Do BaseEntity sử dụng TenantListener để tự động ghi nhận tenant ID từ TenantContext,
 * dịch vụ này cần quản lý TenantContext một cách cẩn thận khi tạo danh mục.
 * Xem {@link #createDefaultCategory(String)} để biết chi tiết.</p>
 * 
 * @author Project Team
 * @version 1.0
 * @see Category
 * @see CategoryRepository
 * @see TenantContext
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * Lấy danh sách tất cả danh mục hoạt động, sắp xếp theo thứ tự hiển thị.
     * 
     * <p>Phương thức này truy vấn tất cả danh mục có isActive = true
     * cho tenant hiện tại (thông qua TenantContext), sắp xếp theo
     * displayOrder từ nhỏ đến lớn (ascending).</p>
     * 
     * <p><b>Thứ tự kết quả:</b> Danh mục với displayOrder nhỏ hơn sẽ hiển thị trước.
     * Ví dụ: displayOrder 1, 2, 3, 9999...</p>
     * 
     * <p><b>Lọc:</b> Chỉ trả về danh mục với isActive = true.
     * Danh mục đã bị soft delete (isDeleted = true) sẽ tự động bị loại bỏ
     * bởi @SQLRestriction của BaseEntity.</p>
     * 
     * @return {@link List<Category>} danh sách danh mục hoạt động,
     *         được sắp xếp theo displayOrder tăng dần.
     *         Nếu không có danh mục nào, trả về danh sách rỗng.
     * 
     * @see Category#getIsActive()
     * @see Category#getDisplayOrder()
     * @see CategoryRepository#findByIsActiveTrueOrderByDisplayOrderAsc()
     */
    public List<Category> getCategories() {
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Tạo danh mục sản phẩm mới.
     * 
     * <p>Phương thức này tạo một danh mục mới với tên và thứ tự hiển thị được chỉ định.
     * Danh mục sẽ tự động được gán tenant ID thông qua BaseEntity.TenantListener.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Tạo entity Category với các thông tin cung cấp</li>
     *   <li>Thiết lập isActive = true (danh mục hoạt động mặc định)</li>
     *   <li>Lưu vào database (TenantListener sẽ ghi tenant ID)</li>
     *   <li>Trả về entity đã lưu</li>
     * </ol>
     * 
     * @param name Tên danh mục, không được phép null hoặc rỗng.
     *             Ví dụ: "Cơm", "Nước uống", "Tráng miệng"
     * @param order Thứ tự hiển thị (displayOrder) trong danh sách.
     *              Giá trị nhỏ sẽ hiển thị trước. Ví dụ: 1, 2, 3...
     * 
     * @return {@link Category} entity đã được tạo và lưu vào database,
     *         bao gồm ID, tenant ID, và các timestamp
     * 
     * @throws org.springframework.dao.DataAccessException
     *         nếu xảy ra lỗi khi lưu vào database
     * 
     * @see Category
     * @see CategoryRepository#save(Object)
     */
    public Category createCategory(String name, Integer order) {
        Category category = Category.builder()
                .name(name)
                .displayOrder(order)
                .isActive(true)
                .build();
        return categoryRepository.save(category);
    }

    /**
     * Tạo danh mục mặc định cho một tenant.
     * 
     * <p>Phương thức này được sử dụng để tạo danh mục "Khác" mặc định khi một tenant mới
     * được tạo. Danh mục này có thể dùng làm danh mục mặc định cho các sản phẩm/thực đơn
     * không thuộc danh mục cụ thể nào.</p>
     * 
     * <p><b>Quy trình thực hiện:</b></p>
     * <ol>
     *   <li>Lưu lại TenantContext hiện tại (nếu có) để tránh mất thông tin</li>
     *   <li>Thiết lập TenantContext tới tenant ID được truyền vào</li>
     *   <li>Tạo mới entity Category với các thông tin mặc định:
     *       <ul>
     *         <li>name: "Khác" (hoặc "Others")</li>
     *         <li>displayOrder: 9999 (để luôn nằm cuối cùng)</li>
     *         <li>isActive: true</li>
     *         <li>isDefault: true (đánh dấu là danh mục mặc định)</li>
     *       </ul>
     *   </li>
     *   <li>Lưu Category vào database (TenantListener sẽ tự động ghi tenant ID từ TenantContext)</li>
     *   <li>Khôi phục TenantContext về trạng thái cũ (trong finally block)</li>
     * </ol>
     * 
     * <p><b>Cơ chế TenantContext:</b><br>
     * Vì BaseEntity sử dụng @PrePersist listener để lấy tenant ID từ TenantContext,
     * phương thức này phải thiết lập TenantContext tạm thời trước khi save entity.
     * Điều này đảm bảo Category được tạo sẽ có đúng tenant ID.<br>
     * 
     * Bằng cách lưu lại context cũ trong try-finally, chúng ta đảm bảo không ảnh hưởng
     * đến request flow của luồng xử lý chính.</p>
     * 
     * <p><b>Ví dụ sử dụng:</b></p>
     * <pre>
     * {@code
     * categoryService.createDefaultCategory("tenant-123");
     * }
     * </pre>
     * 
     * @param tenantId ID của tenant cần tạo danh mục mặc định.
     *                 Không được phép null hoặc rỗng.
     * 
     * @throws IllegalArgumentException nếu tenantId là null hoặc rỗng
     * @throws DataAccessException nếu xảy ra lỗi khi lưu vào database
     * 
     * @see TenantContext#setTenantId(String)
     * @see TenantContext#getTenantId()
     * @see TenantContext#clear()
     */
    public void createDefaultCategory(String tenantId) {
        String oldContext = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantId);
            
            Category defaultCat = Category.builder()
                    .name("Khác") 
                    .displayOrder(9999) 
                    .isActive(true)
                    .isDefault(true) 
                    .build();
            
            categoryRepository.save(defaultCat);
            
        } finally {
            if (oldContext != null) TenantContext.setTenantId(oldContext);
            else TenantContext.clear();
        }
    }

    /**
     * Cập nhật thông tin danh mục (tên và thứ tự hiển thị).
     * 
     * <p>Phương thức này cho phép cập nhật tên và thứ tự hiển thị của một danh mục.
     * Tuy nhiên, danh mục mặc định không được phép chỉnh sửa để bảo vệ tính
     * toàn vẹn của hệ thống.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tìm danh mục theo ID, throw exception nếu không tìm thấy</li>
     *   <li>Kiểm tra xem danh mục có phải mặc định (isDefault=true) không</li>
     *   <li>Nếu là danh mục mặc định, throw exception (không cho phép chỉnh sửa)</li>
     *   <li>Cập nhật tên danh mục (name)</li>
     *   <li>Cập nhật thứ tự hiển thị (displayOrder)</li>
     *   <li>Lưu thay đổi vào database</li>
     *   <li>Trả về danh mục đã cập nhật</li>
     * </ol>
     * 
     * <p><b>Bảo vệ dữ liệu:</b></p>
     * <ul>
     *   <li>Danh mục mặc định không thể chỉnh sửa</li>
     *   <li>Chỉ cập nhật tên và thứ tự hiển thị, các trường khác giữ nguyên</li>
     *   <li>Sử dụng @Transactional đảm bảo tính nhất quán</li>
     * </ul>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>updatedAt sẽ tự động được cập nhật bởi @UpdateTimestamp</li>
     *   <li>Danh mục mặc định (isDefault=true) được bảo vệ khỏi chỉnh sửa</li>
     *   <li>ID danh mục không thể thay đổi</li>
     * </ul>
     * 
     * @param id ID của danh mục cần cập nhật.
     *           Danh mục phải tồn tại và không được là danh mục mặc định.
     * @param newName Tên mới của danh mục, không được phép null hoặc rỗng.
     *                Ví dụ: "Cơm nóng", "Đồ uống lạnh"
     * @param newOrder Thứ tự hiển thị mới (displayOrder).
     *                 Giá trị nhỏ sẽ hiển thị trước. Ví dụ: 1, 2, 3...
     * 
     * @return {@link Category} entity danh mục đã được cập nhật, bao gồm
     *         ID, tên mới, thứ tự mới, và thời gian cập nhật (updatedAt)
     * 
     * @throws RuntimeException("Not found")
     *         nếu không tìm thấy danh mục với ID được chỉ định
     * @throws RuntimeException("KHÔNG THỂ chỉnh sửa danh mục mặc định.")
     *         nếu cố gắng chỉnh sửa danh mục mặc định
     * @throws org.springframework.dao.DataAccessException nếu xảy ra lỗi database
     * 
     * @see Category#getName()
     * @see Category#getDisplayOrder()
     * @see Category#getIsDefault()
     */
    @Transactional
    public Category updateCategory(Integer id, String newName, Integer newOrder) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(404,"Not found"));

        if (Boolean.TRUE.equals(category.getIsDefault())) {
            throw new AppException(400, "KHÔNG THỂ chỉnh sửa danh mục mặc định.");
        }

        category.setName(newName);
        category.setDisplayOrder(newOrder);
        return categoryRepository.save(category);
    }

    /**
     * Xóa danh mục sản phẩm (soft delete).
     * 
     * <p>Phương thức này xóa một danh mục và tất cả sản phẩm của nó sẽ được
     * di chuyển tới danh mục mặc định. Xóa sẽ sử dụng soft delete
     * (đánh dấu isDeleted = true, không xóa vật lý).</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tìm danh mục theo categoryId, throw exception nếu không tìm thấy</li>
     *   <li>Kiểm tra xem danh mục có phải mặc định (isDefault=true) không</li>
     *   <li>Nếu là danh mục mặc định, throw exception (không cho phép xóa)</li>
     *   <li>Tìm danh mục mặc định trong hệ thống</li>
     *   <li>Di chuyển tất cả sản phẩm từ danh mục bị xóa tới danh mục mặc định</li>
     *   <li>Xóa danh mục (sẽ kích hoạt soft delete qua @SQLDelete)</li>
     * </ol>
     * 
     * <p><b>Bảo vệ dữ liệu:</b></p>
     * <ul>
     *   <li>Danh mục mặc định không thể bị xóa</li>
     *   <li>Sản phẩm không bị mất, sẽ được di chuyển tới danh mục mặc định</li>
     *   <li>Sử dụng soft delete để bảo lưu lịch sử</li>
     * </ul>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     *   <li>Phương thức sử dụng @Transactional để đảm bảo tính toàn vẹn dữ liệu</li>
     *   <li>Nếu không tìm thấy danh mục mặc định, sẽ throw exception (lỗi hệ thống)</li>
     *   <li>Tất cả sản phẩm sẽ được giữ lại, chỉ là thay đổi danh mục</li>
     * </ul>
     * 
     * @param categoryId ID của danh mục cần xóa.
     *                   Danh mục phải tồn tại và không được là danh mục mặc định.
     * 
     * @throws RuntimeException("Danh mục không tồn tại")
     *         nếu không tìm thấy danh mục với ID được chỉ định
     * @throws RuntimeException("KHÔNG THỂ tác động vào danh mục mặc định của hệ thống.")
     *         nếu cố gắng xóa danh mục mặc định
     * @throws RuntimeException("Lỗi hệ thống: Không tìm thấy danh mục mặc định. Vui lòng liên hệ Admin.")
     *         nếu hệ thống không tìm thấy danh mục mặc định (lỗi dữ liệu)
     * @throws org.springframework.dao.DataAccessException nếu xảy ra lỗi database
     * 
     * @see Category#getIsDefault()
     * @see ProductRepository#moveProductsToCategory(Integer, Integer)
     * @see CategoryRepository#delete(Object)
     */
    @Transactional
    public void deleteCategory(Integer categoryId) {
        Category categoryToDelete = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(404,"Danh mục không tồn tại"));

        if (Boolean.TRUE.equals(categoryToDelete.getIsDefault())) {
            throw new AppException(400, "KHÔNG THỂ tác động vào danh mục mặc định của hệ thống.");
        }

        Category defaultCategory = categoryRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new AppException(500, "Lỗi hệ thống: Không tìm thấy danh mục mặc định. Vui lòng liên hệ Admin."));
        productRepository.moveProductsToCategory(categoryToDelete.getId(), defaultCategory.getId());

        categoryRepository.delete(categoryToDelete);
    }
}
