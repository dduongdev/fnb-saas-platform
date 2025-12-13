package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.global.dto.CreateTenantRequest;
import com.project.fnb.modules.global.dto.UpdateTenantRequest; // Nhớ tạo DTO này
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.menu.service.CategoryService; // Inject CategoryService
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service quản lý Tenant (Quán hàng) trong hệ thống FnB SaaS.
 * 
 * <p>Class này cung cấp các business logic cho việc tạo, cập nhật, và truy vấn thông tin tenant.
 * Mỗi tenant là một quán hàng độc lập với tài nguyên riêng (MinIO bucket, database).</p>
 * 
 * <p><b>Các tính năng chính:</b></p>
 * <ul>
 *   <li>Tạo tenant mới với bucket MinIO riêng, logo upload, và category mặc định</li>
 *   <li>Cập nhật thông tin tenant (tên, địa chỉ, logo) - partial update</li>
 *   <li>Lấy danh sách tenant của owner (người sở hữu)</li>
 *   <li>Lấy chi tiết một tenant</li>
 * </ul>
 * 
 * <p><b>Multi-Tenancy Architecture:</b></p>
 * <ul>
 *   <li>Mỗi tenant có bucket MinIO riêng: "tenant-{tenantId}-assets"</li>
 *   <li>TenantContext được set/clear để isolate dữ liệu trong request scope</li>
 *   <li>CategoryService và StorageService phụ thuộc vào TenantContext</li>
 *   <li>Authorization: chỉ owner mới có thể cập nhật tenant của họ</li>
 * </ul>
 * 
 * <p><b>Dependencies:</b></p>
 * <ul>
 *   <li>TenantRepository - truy vấn database</li>
 *   <li>MinioClient - tạo bucket và quản lý storage</li>
 *   <li>CategoryService - tạo category mặc định</li>
 *   <li>StorageService - upload file và quản lý URL</li>
 *   <li>TenantContext - ThreadLocal tenant ID storage</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see Tenant
 * @see TenantRepository
 * @see TenantContext
 * @see StorageService
 * @see CategoryService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final MinioClient minioClient;
    private final CategoryService categoryService;
    private final StorageService storageService; // Inject thêm StorageService

    /**
     * Tạo mới tenant (quán hàng) với bucket MinIO riêng, logo, và category mặc định.
     * 
     * <p>Quy trình tạo tenant gồm các bước:</p>
     * <ol>
     *   <li>Tạo entity Tenant trong database để lấy ID (UUID)</li>
     *   <li>Tạo bucket MinIO riêng: "tenant-{tenantId}-assets" với policy public read</li>
     *   <li>Upload logo quán (nếu có) vào bucket MinIO</li>
     *   <li>Tạo category mặc định "Khác" để khởi tạo menu</li>
     * </ol>
     * 
     * <p><b>Multi-Tenancy Context:</b></p>
     * <ul>
     *   <li>Cách bước 3-4, TenantContext được set với tenant ID mới</li>
     *   <li>Điều này đảm bảo CategoryService và StorageService upload vào đúng bucket</li>
     *   <li>TenantContext được clear/restore trong finally block để không ảnh hưởng request sau</li>
     * </ul>
     * 
     * <p><b>Logo Upload:</b></p>
     * <ul>
     *   <li>Logo là optional (nullable)</li>
     *   <li>Nếu có logo, được upload bởi StorageService.uploadTenantImage()</li>
     *   <li>URL logo được lưu vào Tenant.logoUrl</li>
     * </ul>
     * 
     * <p><b>MinIO Bucket Setup:</b></p>
     * <ul>
     *   <li>Bucket name: "tenant-{tenantId}-assets" (lowercase)</li>
     *   <li>Policy: Public read (s3:GetObject cho mọi principal)</li>
     *   <li>Cho phép client xem hình ảnh món ăn, logo quán</li>
     * </ul>
     * 
     * <p><b>Transaction:</b></p>
     * <ul>
     *   <li>@Transactional bảo đảm consistency của database operations</li>
     *   <li>Nếu có exception, tất cả changes được rollback</li>
     * </ul>
     * 
     * @param request CreateTenantRequest chứa name, address của quán
     * @param ownerId User ID của owner (chủ nhân) quán, lấy từ JWT token
     * @param logo File hình ảnh logo quán (optional, multipart upload)
     * 
     * @return Tenant object đã được tạo, chứa:
     *         - id (UUID auto-generated)
     *         - name, address từ request
     *         - logoUrl (nếu upload logo thành công)
     *         - ownerId, isActive=true, createdAt, updatedAt
     * 
     * @throws RuntimeException nếu xảy ra lỗi khi tạo bucket MinIO hoặc upload logo
     * @throws RuntimeException nếu xảy ra lỗi khi tạo category mặc định
     * @throws DataIntegrityViolationException nếu dữ liệu invalid
     * 
     * @see CreateTenantRequest
     * @see StorageService#uploadTenantImage(MultipartFile)
     * @see CategoryService#createDefaultCategory(String)
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request, String ownerId, MultipartFile logo) {
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .address(request.getAddress())
                .ownerId(ownerId)
                .isActive(true)
                .build();
        
        tenant = tenantRepository.save(tenant);

        String bucketName = "tenant-" + tenant.getId().toLowerCase() + "-assets";
        createBucketSafe(bucketName);

        String oldContext = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenant.getId());

            if (logo != null && !logo.isEmpty()) {
                String logoUrl = storageService.uploadTenantImage(logo);
                tenant.setLogoUrl(logoUrl);
                tenant = tenantRepository.save(tenant);
            }

            categoryService.createDefaultCategory(tenant.getId());

        } finally {
            if (oldContext != null) TenantContext.setTenantId(oldContext);
            else TenantContext.clear();
        }

        return tenant;
    }

    /**
     * Cập nhật thông tin tenant (quán hàng).
     * 
     * <p>Hỗ trợ cập nhật partial (chỉ cập nhật những trường được cung cấp).</p>
     * 
     * <p><b>Quy trình cập nhật:</b></p>
     * <ol>
     *   <li>Tìm tenant theo ID, throw NotFoundException nếu không tồn tại</li>
     *   <li>Kiểm tra authorization - chỉ owner mới có thể cập nhật</li>
     *   <li>Cập nhật tên và/hoặc địa chỉ (nếu non-null)</li>
     *   <li>Xử lý logo mới (upload mới, xóa cũ nếu cần)</li>
     *   <li>Lưu thay đổi vào database</li>
     * </ol>
     * 
     * <p><b>Partial Update Logic:</b></p>
     * <ul>
     *   <li>name: nếu null, giữ giá trị cũ</li>
     *   <li>address: nếu null, giữ giá trị cũ</li>
     *   <li>logo: nếu null/empty, giữ logo cũ</li>
     *   <li>nếu logo mới được upload, có thể xóa logo cũ từ MinIO (optional)</li>
     * </ul>
     * 
     * <p><b>Authorization:</b></p>
     * <ul>
     *   <li>Kiểm tra currentUserId vs tenant.ownerId</li>
     *   <li>Nếu không match, throw AppException(403, "Bạn không có quyền...")</li>
     * </ul>
     * 
     * <p><b>Logo Upload:</b></p>
     * <ul>
     *   <li>TenantContext được set với tenantId để upload vào đúng bucket</li>
     *   <li>StorageService.uploadTenantImage() xử lý upload</li>
     *   <li>URL logo mới được save vào tenant.logoUrl</li>
     * </ul>
     * 
     * <p><b>Transaction:</b></p>
     * <ul>
     *   <li>@Transactional bảo đảm consistency</li>
     *   <li>Rollback nếu có exception</li>
     * </ul>
     * 
     * @param tenantId ID của tenant cần cập nhật (bắt buộc)
     * @param request UpdateTenantRequest chứa name, address (optional)
     * @param logo File logo mới (optional, multipart upload)
     * @param currentUserId User ID của người gửi request (lấy từ JWT token)
     * 
     * @return Tenant object đã được cập nhật
     * 
     * @throws AppException(404) nếu tenant không tồn tại
     * @throws AppException(403) nếu currentUserId không phải owner
     * @throws RuntimeException nếu xảy ra lỗi upload logo
     * 
     * @see UpdateTenantRequest
     * @see TenantContext
     */
    @Transactional
    public Tenant updateTenant(String tenantId, UpdateTenantRequest request, MultipartFile logo, String currentUserId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(404, "Quán không tồn tại"));

        if (!tenant.getOwnerId().equals(currentUserId)) {
            throw new AppException(403, "Bạn không có quyền chỉnh sửa quán này");
        }

        if (request.getName() != null) tenant.setName(request.getName());
        if (request.getAddress() != null) tenant.setAddress(request.getAddress());

        if (logo != null && !logo.isEmpty()) {
            String oldContext = TenantContext.getTenantId();
            try {
                TenantContext.setTenantId(tenant.getId());

                storageService.deleteFile(tenant.getLogoUrl());

                String newLogoUrl = storageService.uploadTenantImage(logo);
                tenant.setLogoUrl(newLogoUrl);
            } finally {
                if (oldContext != null) TenantContext.setTenantId(oldContext);
                else TenantContext.clear();
            }
        }

        return tenantRepository.save(tenant);
    }
    
    /**
     * Lấy danh sách tất cả tenant của một owner (người sở hữu).
     * 
     * <p>Truy vấn tất cả tenant từ database và filter theo ownerId.
     * Được gọi bởi API GET /api/tenants/me để lấy danh sách quán của user đăng nhập.</p>
     * 
     * <p><b>Query Logic:</b></p>
     * <ul>
     *   <li>Lấy tất cả Tenant từ database</li>
     *   <li>Filter stream theo tenant.ownerId == ownerId</li>
     *   <li>Trả về List<Tenant> (có thể rỗng nếu owner chưa có quán)</li>
     * </ul>
     * 
     * <p><b>Performance Note:</b></p>
     * <ul>
     *   <li>Hiện tại sử dụng findAll() rồi filter in-memory (không hiệu quả)</li>
     *   <li>Trong tương lai nên dùng custom query: findByOwnerId(ownerId) cho better performance</li>
     * </ul>
     * 
     * @param ownerId User ID của owner (lấy từ JWT token)
     * 
     * @return List<Tenant> danh sách tenant sở hữu bởi owner này (có thể rỗng)
     * 
     * @see TenantRepository
     */
    public List<Tenant> getMyTenants(String ownerId) {
        return tenantRepository.findAll().stream()
                .filter(t -> t.getOwnerId().equals(ownerId))
                .toList();
    }
    
    /**
     * Lấy chi tiết thông tin của một tenant theo ID.
     * 
     * <p>Truy vấn database để lấy Tenant object hoàn chỉnh.
     * Được gọi bởi API GET /api/tenants/{id} (public endpoint, không cần auth).</p>
     * 
     * <p><b>Query Logic:</b></p>
     * <ul>
     *   <li>Tìm tenant theo ID</li>
     *   <li>Nếu không tìm thấy, throw NotFoundException (404)</li>
     *   <li>Trả về Tenant object đầy đủ</li>
     * </ul>
     * 
     * <p><b>Response Data:</b></p>
     * <ul>
     *   <li>id, name, address, logoUrl</li>
     *   <li>ownerId (owner user ID)</li>
     *   <li>isActive (trạng thái quán)</li>
     *   <li>createdAt, updatedAt (timestamps)</li>
     * </ul>
     * 
     * @param tenantId ID của tenant cần lấy chi tiết
     * 
     * @return Tenant object đầy đủ thông tin
     * 
     * @throws AppException(404) nếu tenant không tồn tại
     * 
     * @see Tenant
     */
    public Tenant getTenantDetail(String tenantId) {
         return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(404, "Quán không tồn tại"));
    }

    /**
     * Tạo bucket MinIO mới cho tenant với policy public read (private helper).
     * 
     * <p>Được gọi bởi createTenant() khi khởi tạo quán.
     * Tạo bucket MinIO với tên "tenant-{tenantId}-assets" nếu chưa tồn tại.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Kiểm tra bucket đã tồn tại chưa</li>
     *   <li>Nếu chưa, tạo bucket mới</li>
     *   <li>Set policy public read để client có thể xem ảnh sản phẩm/logo</li>
     *   <li>Log thông tin tạo bucket (hoặc error nếu fail)</li>
     * </ol>
     * 
     * <p><b>MinIO Policy:</b></p>
     * <ul>
     *   <li>Principal: "*" (public)</li>
     *   <li>Action: "s3:GetObject" (read-only)</li>
     *   <li>Resource: "arn:aws:s3:::bucket-name/*" (tất cả files)</li>
     *   <li>Cho phép bất kỳ ai xem ảnh mà không cần auth</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * <ul>
     *   <li>Catch Exception để tránh gián đoạn quá trình tạo tenant</li>
     *   <li>Log error để debugging</li>
     *   <li>Không throw exception, để phần sau có thể tiếp tục</li>
     * </ul>
     * 
     * @param bucketName Tên bucket cần tạo (format: "tenant-{id}-assets")
     * 
     * @throws Exception được catch lại và log, không propagate
     */
    private void createBucketSafe(String bucketName) {
        try {
            boolean found = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucketName);
                minioClient.setBucketPolicy(io.minio.SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
                log.info("Created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
        }
    }

    @Transactional
    public void updateTenantStatus(String tenantId, Boolean status, String currentUserId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(404, "Quán không tồn tại"));

        if (!tenant.getOwnerId().equals(currentUserId)) {
            throw new AppException(403, "Bạn không có quyền thực hiện thao tác này");
        }

        tenant.setIsActive(status);
        tenantRepository.save(tenant);
    }
}