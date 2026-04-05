package com.project.fnb.modules.menu.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.menu.dto.ProductResponse;
import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.entity.ProductImage;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.menu.repository.ProductImageRepository;
import com.project.fnb.modules.menu.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dịch vụ quản lý sản phẩm (Product) trong hệ thống menu.
 * 
 * <p>Class này cung cấp các chức năng toàn diện để quản lý sản phẩm, bao gồm tạo, cập nhật,
 * xóa, quản lý hình ảnh sản phẩm, và truy vấn chi tiết. Hỗ trợ tải lên hình ảnh lên cloud storage
 * thông qua StorageService. Tất cả dữ liệu được quản lý theo tenant thông qua TenantContext.</p>
 * 
 * <p><b>Tính năng chính:</b></p>
 * <ul>
 *   <li>Tạo sản phẩm mới với hình ảnh</li>
 *   <li>Cập nhật thông tin sản phẩm (tên, giá, mô tả, danh mục, trạng thái)</li>
 *   <li>Quản lý hình ảnh sản phẩm (thêm, xóa, đặt hình ảnh chính)</li>
 *   <li>Xóa sản phẩm (soft delete)</li>
 *   <li>Truy vấn chi tiết sản phẩm</li>
 *   <li>Chuyển đổi entity sang DTO response</li>
 * </ul>
 * 
 * <p><b>Quy tắc kinh doanh:</b></p>
 * <ul>
 *   <li>Sản phẩm phải thuộc một danh mục tồn tại</li>
 *   <li>Hình ảnh đầu tiên sẽ được đặt là isPrimary=true (hình ảnh chính)</li>
 *   <li>Khi tải lên hình ảnh, chúng được lưu trên cloud storage (không lưu local)</li>
 *   <li>Xóa sản phẩm sử dụng soft delete (isDeleted=true)</li>
 *   <li>Hình ảnh được sắp xếp theo displayOrder</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see Product
 * @see ProductImage
 * @see Category
 * @see StorageService
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    /**
     * Lấy danh sách sản phẩm theo tenant hiện tại.
     * 
     * <p>Phương thức này truy vấn danh sách sản phẩm theo tenant từ TenantContext.
     * Hỗ trợ filter theo categoryId và status. Kết quả được phân trang.</p>
     * 
     * @param categoryId Filter theo danh mục (optional, null = tất cả)
     * @param status Filter theo trạng thái (optional, null = tất cả)
     * @param pageable Thông tin phân trang
     * 
     * @return Page<ProductResponse> danh sách sản phẩm đã filter và phân trang
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Integer categoryId, Product.ProductStatus status, Pageable pageable) {
        Page<Product> products;
        
        if (categoryId != null && status != null) {
            products = productRepository.findByCategoryIdAndStatus(categoryId, status, pageable);
        } else if (categoryId != null) {
            products = productRepository.findByCategoryId(categoryId, pageable);
        } else if (status != null) {
            products = productRepository.findByStatus(status, pageable);
        } else {
            // Fix N+1 query: Use findAllWithImages() to eager load all images
            // Before: 1 query + N queries (for each product's images)
            // After: 1 query with JOIN FETCH
            products = productRepository.findAllWithImages(pageable);
        }
        
        return products.map(this::mapToResponse);
    }

    /**
     * Tạo sản phẩm mới với hình ảnh trong danh mục được chỉ định.
     * 
     * <p>Phương thức này tạo một sản phẩm mới với thông tin cơ bản (tên, giá, mô tả)
     * và xử lý tải lên hình ảnh nếu có. Hình ảnh được tải lên cloud storage
     * thông qua StorageService.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tìm danh mục theo categoryId, throw exception nếu không tồn tại</li>
     *   <li>Tạo entity Product với thông tin cung cấp</li>
     *   <li>Thiết lập status = AVAILABLE (mặc định)</li>
     *   <li>Lưu Product vào database lần đầu</li>
     *   <li>Nếu có files, gọi uploadImages() để xử lý tải lên</li>
     *   <li>Chuyển đổi Product sang ProductResponse và trả về</li>
     * </ol>
     * 
     * @param categoryId ID của danh mục chứa sản phẩm. Danh mục phải tồn tại.
     * @param name Tên sản phẩm, không được phép null hoặc rỗng.
     * @param price Giá bán của sản phẩm, không được phép null.
     * @param desc Mô tả chi tiết sản phẩm (có thể null hoặc rỗng).
     * @param files Danh sách hình ảnh sản phẩm (có thể null hoặc rỗng).
     * 
     * @return {@link ProductResponse} chứa thông tin sản phẩm đã tạo
     * @throws AppException(404, "Danh mục không tồn tại") nếu categoryId không tồn tại
     * 
     * @see #uploadImages(Product, List)
     * @see #mapToResponse(Product)
     */
    @Transactional
    public ProductResponse createProduct(Integer categoryId, String name, BigDecimal price, 
                                         String desc, List<MultipartFile> files) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(404, "Danh mục không tồn tại"));

        Product product = Product.builder()
                .category(category)
                .name(name)
                .price(price)
                .description(desc)
                .status(Product.ProductStatus.AVAILABLE)
                .build();
        
        product = productRepository.save(product);

        // Upload ảnh
        if (files != null && !files.isEmpty()) {
            List<ProductImage> images = uploadImages(product, files);
            product.setImages(images);
        }

        return mapToResponse(product);
    }

    /**
     * Cập nhật thông tin sản phẩm (không bao gồm hình ảnh).
     * 
     * <p>Phương thức này cho phép cập nhật các thuộc tính cơ bản của sản phẩm.
     * Các tham số null sẽ không được cập nhật (optional update).</p>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>Phương thức sử dụng @Transactional để đảm bảo tính nhất quán</li>
     *   <li>Danh mục mới (nếu cập nhật) phải tồn tại</li>
     *   <li>Không cập nhật hình ảnh, sử dụng addImages() hoặc removeImage()</li>
     * </ul>
     * 
     * @param productId ID của sản phẩm cần cập nhật. Sản phẩm phải tồn tại.
     * @param name Tên sản phẩm mới (optional, null = giữ nguyên).
     * @param price Giá bán mới (optional, null = giữ nguyên).
     * @param desc Mô tả sản phẩm mới (optional, null = giữ nguyên).
     * @param categoryId ID danh mục mới (optional, null = giữ nguyên).
     * @param status Trạng thái sản phẩm mới (optional, null = giữ nguyên).
     * 
     * @return {@link ProductResponse} chứa thông tin sản phẩm đã cập nhật
     * @throws AppException(404, "Sản phẩm không tồn tại") nếu productId không tồn tại
     * @throws AppException(404, "Danh mục không tồn tại") nếu categoryId không tồn tại
     * 
     * @see #mapToResponse(Product)
     */
    @Transactional
    public ProductResponse updateProductInfo(Long productId, String name, BigDecimal price, 
                                             String desc, Integer categoryId, Product.ProductStatus status) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(404, "Sản phẩm không tồn tại"));

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new AppException(404, "Danh mục không tồn tại"));
            product.setCategory(category);
        }
        if (name != null) product.setName(name);
        if (price != null) product.setPrice(price);
        if (desc != null) product.setDescription(desc);
        if (status != null) product.setStatus(status);

        return mapToResponse(productRepository.save(product));
    }

    /**
     * Thêm hình ảnh mới cho sản phẩm hiện có.
     * 
     * <p>Phương thức này cho phép tải lên thêm hình ảnh cho một sản phẩm đã tồn tại.
     * Các hình ảnh sẽ được tải lên cloud storage, và displayOrder sẽ được tính dựa trên
     * số lượng hình ảnh hiện có của sản phẩm.</p>
     * 
     * <p><b>Quy tắc về hình ảnh chính (isPrimary):</b></p>
     * <ul>
     *   <li>Nếu sản phẩm chưa có hình ảnh nào (currentSize=0), hình ảnh đầu tiên sẽ isPrimary=true</li>
     *   <li>Nếu sản phẩm đã có hình ảnh, hình ảnh mới sẽ isPrimary=false</li>
     * </ul>
     * 
     * @param productId ID của sản phẩm cần thêm hình ảnh. Sản phẩm phải tồn tại.
     * @param files Danh sách hình ảnh cần tải lên (có thể null hoặc rỗng).
     * 
     * @throws AppException(404, "Sản phẩm không tồn tại") nếu productId không tồn tại
     * 
     * @see #uploadImages(Product, List)
     */
    @Transactional
    public void addImages(Long productId, List<MultipartFile> files) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(404, "Sản phẩm không tồn tại"));
        
        if (files != null && !files.isEmpty()) {
            List<ProductImage> newImages = uploadImages(product, files);
            product.getImages().addAll(newImages);
            productRepository.save(product);
        }
    }

    /**
     * Xóa hình ảnh cụ thể của sản phẩm.
     * 
     * <p>Phương thức này xóa một hình ảnh duy nhất dựa trên imageId.
     * Hình ảnh sẽ bị soft delete (đánh dấu isDeleted=true).</p>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>Phương thức sử dụng @Transactional để đảm bảo tính nhất quán</li>
     *   <li>Xóa soft delete (isDeleted=true), không xóa vật lý</li>
     *   <li>Cloud storage URL vẫn tồn tại, cần xử lý riêng nếu muốn xóa file</li>
     * </ul>
     * 
     * @param imageId ID của hình ảnh cần xóa. Hình ảnh phải tồn tại.
     * 
     * @throws org.springframework.dao.EmptyResultDataAccessException nếu imageId không tồn tại
     * 
     * @see ProductImageRepository#deleteById(Object)
     */
    @Transactional
    public void removeImage(Long imageId) {
        productImageRepository.deleteById(imageId);
    }

    /**
     * Xóa sản phẩm (soft delete).
     * 
     * <p>Phương thức này xóa mềm (soft delete) một sản phẩm bằng cách đánh dấu
     * isDeleted=true. Sản phẩm sẽ không được hiển thị trong các query thông thường
     * nhưng dữ liệu vẫn được lưu trữ cho mục đích audit.</p>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     *   <li>Phương thức sử dụng @Transactional để đảm bảo tính nhất quán</li>
     *   <li>Đây là soft delete, không phải hard delete vật lý</li>
     *   <li>Hình ảnh của sản phẩm vẫn được lưu trong ProductImage table</li>
     *   <li>Dữ liệu có thể được khôi phục nếu cần cho mục đích audit</li>
     * </ul>
     * 
     * @param productId ID của sản phẩm cần xóa. Sản phẩm phải tồn tại.
     * 
     * @throws AppException(404, "Sản phẩm không tồn tại") nếu productId không tồn tại
     * 
     * @see BaseEntity
     * @see Product#getIsDeleted()
     */
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(404, "Sản phẩm không tồn tại"));
        productRepository.delete(product); 
    }

    /**
     * Lấy chi tiết sản phẩm theo ID.
     * 
     * <p>Phương thức này truy vấn sản phẩm từ database theo productId và chuyển đổi
     * thành ProductResponse DTO. Chi tiết sản phẩm bao gồm tất cả thông tin cơ bản
     * và danh sách hình ảnh.</p>
     * 
     * <p><b>Thông tin trả về:</b></p>
     * <ul>
     *   <li>ID, tên, giá, mô tả sản phẩm</li>
     *   <li>Trạng thái sản phẩm (AVAILABLE, UNAVAILABLE, v.v.)</li>
     *   <li>Danh mục: ID và tên danh mục</li>
     *   <li>Danh sách hình ảnh: ID, URL, isPrimary, displayOrder</li>
     * </ul>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>Phương thức không sử dụng @Transactional (chỉ đọc)</li>
     *   <li>Sản phẩm đã bị soft delete sẽ tự động bị loại bỏ khỏi kết quả</li>
     * </ul>
     * 
     * @param id ID của sản phẩm cần lấy chi tiết. Sản phẩm phải tồn tại.
     * 
     * @return {@link ProductResponse} chứa chi tiết sản phẩm bao gồm:
     *         - ID, tên, giá, mô tả
     *         - ID danh mục, tên danh mục
     *         - Trạng thái (AVAILABLE, UNAVAILABLE, v.v.)
     *         - Danh sách ImageDto (id, url, isPrimary)
     * 
     * @throws AppException(404, "Sản phẩm không tồn tại") nếu sản phẩm không tồn tại
     * 
     * @see #mapToResponse(Product)
     */
    public ProductResponse getProductDetail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(404, "Sản phẩm không tồn tại"));
        return mapToResponse(product);
    }

    /**
     * Helper method: Tải lên hình ảnh sản phẩm lên cloud storage.
     * 
     * <p>Phương thức này xử lý tải lên một danh sách hình ảnh cho một sản phẩm.
     * Mỗi hình ảnh sẽ được tải lên cloud storage thông qua StorageService,
     * và các entity ProductImage sẽ được tạo và lưu vào database.</p>
     * 
     * <p><b>Quy tắc hình ảnh chính:</b></p>
     * <ul>
     *   <li>Hình ảnh đầu tiên của sản phẩm (i=0) sẽ là isPrimary=true</li>
     *   <li>Nếu sản phẩm đã có hình ảnh (currentSize>0), hình ảnh mới sẽ isPrimary=false</li>
     *   <li>Chỉ một hình ảnh có thể là isPrimary=true</li>
     * </ul>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>Phương thức private, chỉ được sử dụng nội bộ trong service</li>
     *   <li>File rỗng sẽ bị bỏ qua tự động</li>
     *   <li>displayOrder sẽ tiếp nối từ các hình ảnh hiện có của sản phẩm</li>
     *   <li>Các hình ảnh sẽ được lưu trên cloud storage, không local</li>
     * </ul>
     * 
     * @param product Sản phẩm cần tải lên hình ảnh. Product phải đã được lưu trong database để có ID.
     * @param files Danh sách file hình ảnh cần tải lên. Có thể chứa file rỗng.
     * 
     * @return {@link List<ProductImage>} danh sách ProductImage đã được tạo, 
     *         lưu trên cloud, và lưu vào database.
     * 
     * @see StorageService#uploadTenantImage(MultipartFile)
     * @see ProductImageRepository#saveAll(Iterable)
     */
    private List<ProductImage> uploadImages(Product product, List<MultipartFile> files) {
        List<ProductImage> images = new ArrayList<>();
        int currentSize = product.getImages() != null ? product.getImages().size() : 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) continue;

            String url = storageService.uploadTenantImage(file);
            boolean isPrimary = (currentSize == 0 && i == 0); 

            ProductImage img = ProductImage.builder()
                    .product(product)
                    .imageUrl(url)
                    .isPrimary(isPrimary)
                    .displayOrder(currentSize + i)
                    .build();
            images.add(img);
        }
        return productImageRepository.saveAll(images);
    }

    /**
     * Helper method: Chuyển đổi Product entity sang ProductResponse DTO.
     * 
     * <p>Phương thức này thực hiện chuyển đổi từ Product entity sang ProductResponse DTO
     * (Data Transfer Object) để sử dụng trong API responses. Quá trình này bao gồm
     * lấy thông tin từ entity, chuyển đổi hình ảnh, và xử lý các trường nullable.</p>
     * 
     * <p><b>Thông tin chuyển đổi:</b></p>
     * <ul>
     *   <li>Product.id → ProductResponse.id</li>
     *   <li>Product.name → ProductResponse.name</li>
     *   <li>Product.price → ProductResponse.price</li>
     *   <li>Product.description → ProductResponse.description</li>
     *   <li>Product.status (enum) → ProductResponse.status (string name)</li>
     *   <li>Product.category.id → ProductResponse.categoryId</li>
     *   <li>Product.category.name → ProductResponse.categoryName</li>
     *   <li>Product.images → ProductResponse.images (list of ImageDto)</li>
     * </ul>
     * 
     * <p><b>Xử lý null/empty:</b></p>
     * <ul>
     *   <li>Nếu category là null, categoryId và categoryName sẽ là null</li>
     *   <li>Nếu images là null, imgDtos sẽ là empty list</li>
     *   <li>Status enum sẽ được chuyển thành string name (ví dụ: "AVAILABLE")</li>
     * </ul>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>Phương thức private, chỉ được sử dụng nội bộ trong service</li>
     *   <li>Sử dụng Stream API và Collectors.toList() cho danh sách hình ảnh</li>
     *   <li>Phương thức không thay đổi entity, chỉ đọc dữ liệu</li>
     * </ul>
     * 
     * @param p Product entity cần chuyển đổi. Không được null.
     * 
     * @return {@link ProductResponse} DTO chứa tất cả thông tin sản phẩm
     *         dưới dạng có thể serialized thành JSON cho API response.
     * 
     * @see ProductResponse
     * @see ProductResponse.ImageDto
     */
    private ProductResponse mapToResponse(Product p) {
        List<ProductResponse.ImageDto> imgDtos = new ArrayList<>();
        if (p.getImages() != null) {
            imgDtos = p.getImages().stream()
                    .map(img -> ProductResponse.ImageDto.builder()
                            .id(img.getId())
                            .url(img.getImageUrl())
                            .isPrimary(img.getIsPrimary())
                            .build())
                    .collect(Collectors.toList());
        }

        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .description(p.getDescription())
                .status(p.getStatus().name())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .images(imgDtos)
                .build();
    }
}