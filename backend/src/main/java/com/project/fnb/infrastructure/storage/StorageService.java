package com.project.fnb.infrastructure.storage;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Dịch vụ quản lý lưu trữ file trên MinIO (S3-compatible object storage).
 * 
 * <p>Class này cung cấp các chức năng để tải lên và xóa file trên MinIO server.
 * Hỗ trợ hai loại bucket: tenant-specific bucket (cho ảnh sản phẩm) và
 * shared bucket (cho ảnh profile người dùng). Mỗi file được lưu trữ
 * theo cấu trúc thư mục rõ ràng để dễ quản lý và truy cập.</p>
 * 
 * <p><b>Quan trọng - URL Strategy:</b></p>
 * <ul>
 * <li><b>Internal URL:</b> Được sử dụng bởi MinioClient (backend) để giao tiếp với MinIO server.
 *     Ví dụ: "http://minio:9000" (DNS name hoặc IP nội bộ trong Docker network)</li>
 * <li><b>External URL:</b> Được trả về cho client/frontend để truy cập file.
 *     Ví dụ: "http://localhost:9000" hoặc "https://minio.example.com" (domain công cộng)</li>
 * </ul>
 * 
 * <p><b>Các bucket được hỗ trợ:</b></p>
 * <ul>
 * <li><b>Tenant Buckets:</b> "tenant-{tenantId}-assets" - Lưu trữ ảnh sản phẩm, menu, v.v. của từng tenant</li>
 * <li><b>User Bucket:</b> Bucket công cộng (configurable) - Lưu trữ ảnh profile người dùng</li>
 * </ul>
 * 
 * <p><b>Cấu trúc URL trả về (External):</b></p>
 * <pre>
 * {@code
 * http://localhost:9000/bucket-name/folder/file-uuid-originalname
 * Ví dụ: http://localhost:9000/tenant-abc123-assets/images/a1b2c3d4-e5f6-product.jpg
 * }
 * </pre>
 * 
 * <p><b>Tính năng chính:</b></p>
 * <ul>
 * <li>Tải lên ảnh tenant (sản phẩm, menu, v.v.) vào tenant-specific bucket</li>
 * <li>Tải lên ảnh profile người dùng vào shared bucket</li>
 * <li>Xóa file theo URL External (soft delete)</li>
 * <li>Tự động tạo UUID để tránh trùng tên file</li>
 * <li>Logging chi tiết cho việc upload/delete</li>
 * <li>Phân biệt internal URL (backend) và external URL (client)</li>
 * </ul>
 * 
 * <p><b>Quy tắc lưu trữ:</b></p>
 * <ul>
 * <li>Mỗi tenant có bucket riêng: "tenant-{tenantId}-assets"</li>
 * <li>File được tổ chức theo thư mục: /images/, /avatars/, v.v.</li>
 * <li>Filename được tạo: "{prefix}{UUID}-{originalname}" để tránh trùng lặp</li>
 * <li>Dữ liệu tenant hoàn toàn tách biệt về lưu trữ</li>
 * </ul>
 * 
 * <p><b>Cấu hình MinIO:</b></p>
 * <p>Cần cấu hình trong application.yml:</p>
 * <pre>
 * {@code
 * minio:
 *   url: http://minio:9000              # Internal URL (backend sử dụng)
 *   external-url: http://localhost:9000 # External URL (client sử dụng)
 *   buckets:
 *     user-profiles: user-profiles
 * }
 * </pre>
 * 
 * <p><b>Lưu ý quan trọng:</b></p>
 * <ul>
 * <li>uploadTenantImage() yêu cầu TenantContext đã được set (thông qua TenantFilter)</li>
 * <li>deleteFile() nhận External URL từ client và chuyển đổi thành object name</li>
 * <li>MinioClient sử dụng Internal URL để giao tiếp với MinIO server</li>
 * <li>Client nhận External URL để truy cập file qua internet</li>
 * <li>Tất cả file được tải lên đều có public URL có thể access</li>
 * <li>UUID được thêm vào filename để tránh trùng lặp khi upload cùng file</li>
 * </ul>
 * 
 * <p><b>Flow Chi Tiết:</b></p>
 * <ol>
 * <li>Client gửi file + request upload tới backend</li>
 * <li>Backend lấy Internal URL từ MinioClient config</li>
 * <li>Backend upload file lên MinIO sử dụng MinioClient</li>
 * <li>Backend tạo External URL và trả về cho client</li>
 * <li>Client lưu External URL để truy cập file qua internet</li>
 * <li>Khi delete, client gửi External URL về backend</li>
 * <li>Backend parse External URL thành bucket/object name</li>
 * <li>Backend delete file trên MinIO server</li>
 * </ol>
 * 
 * @author Project Team
 * @version 2.0 (with External URL support)
 * @see TenantContext
 * @see MinioClient
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    /**
     * MinIO client được tạo bởi MinioConfig bean.
     * 
     * <p>Được cấu hình sẵn với Internal URL (http://minio:9000)
     * để giao tiếp với MinIO server nội bộ.
     * Client này sẽ tự động xử lý kết nối tới MinIO server.
     * Sử dụng PutObjectArgs để upload file và RemoveObjectArgs để xóa file.</p>
     * 
     * @see MinioClient
     */
    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String internalUrl; // http://minio:9000 (Dùng để log hoặc check nội bộ)

    @Value("${minio.external-url}")
    private String externalUrl;
    
    /**
     * Tên bucket công cộng để lưu trữ ảnh profile người dùng.
     * 
     * <p>Ví dụ: "user-profiles" (không phụ thuộc vào tenant).
     * Bucket này được chia sẻ giữa tất cả các tenant và người dùng.
     * 
     * Cấu hình trong application.yml: minio.buckets.user-profiles</p>
     */
    @Value("${minio.buckets.user-profiles}")
    private String userBucket;

    /**
     * Tải lên ảnh sản phẩm/menu của tenant lên tenant-specific bucket trên MinIO.
     * 
     * <p>Phương thức này tải lên một file ảnh lên bucket riêng của tenant.
     * Bucket name được tạo tự động từ tenant ID: "tenant-{tenantId}-assets".
     * File được lưu trong thư mục /images/ để dễ quản lý.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     * <li>Lấy tenant ID từ TenantContext (được set bởi TenantFilter)</li>
     * <li>Throw exception nếu tenant ID không tồn tại</li>
     * <li>Tạo bucket name: "tenant-{tenantId}-assets"</li>
     * <li>Gọi uploadFile() với bucket name và prefix "images/"</li>
     * <li>Trả về External URL công cộng của file vừa tải lên</li>
     * </ol>
     * 
     * <p><b>Cấu trúc file:</b></p>
     * <ul>
     * <li>Bucket: "tenant-abc123-assets" (abc123 là tenant ID)</li>
     * <li>Path: "images/{UUID}-{originalname}"</li>
     * <li>External URL: "http://localhost:9000/tenant-abc123-assets/images/a1b2c3d4-e5f6-product.jpg"</li>
     * </ul>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     * <li>TenantContext PHẢI được set (thông qua TenantFilter) trước khi gọi phương thức này</li>
     * <li>Nếu tenant ID là null, RuntimeException sẽ được throw</li>
     * <li>File được lưu với UUID prefix để tránh trùng lặp file name</li>
     * <li>Content type được lưu từ file (ví dụ: image/jpeg)</li>
     * <li>Mỗi tenant có bucket riêng biệt, dữ liệu hoàn toàn tách biệt</li>
     * </ul>
     * 
     * <p><b>Ví dụ sử dụng:</b></p>
     * <pre>
     * {@code
     * // TenantContext đã được set bởi TenantFilter
     * String imageUrl = storageService.uploadTenantImage(multipartFile);
     * // Result: "http://localhost:9000/tenant-xyz-assets/images/a1b2c3d4-photo.jpg"
     * }
     * </pre>
     * 
     * @param file MultipartFile chứa ảnh cần tải lên.
     *             Phải không null và không rỗng.
     *             Supported formats: JPEG, PNG, WebP, v.v.
     * 
     * @return {@link String} External URL công cộng của file vừa tải lên.
     *         Định dạng: "http://localhost:9000/bucket-name/path/filename"
     *         Client sử dụng URL này để truy cập file.
     * 
     * @throws RuntimeException("Tenant ID missing") 
     *         nếu TenantContext.getTenantId() trả về null
     * @throws RuntimeException("Upload failed: ...") 
     *         nếu xảy ra lỗi khi tải lên file (kết nối, permission, v.v.)
     * 
     * @see TenantContext#getTenantId()
     * @see #uploadFile(String, MultipartFile, String)
     */
    public String uploadTenantImage(MultipartFile file) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new AppException(400, "Tenant ID missing");
        String bucketName = "tenant-" + tenantId.toLowerCase() + "-assets";
        return uploadFile(bucketName, file, "images/");
    }

    // --- 2. Upload ảnh User Profile ---
    public String uploadUserProfileImage(MultipartFile file) {
        return uploadFile(userBucket, file, "avatars/");
    }

    /**
     * Helper method: Tải lên file lên MinIO bucket cụ thể.
     * 
     * <p>Phương thức private này là helper chung được sử dụng bởi
     * uploadTenantImage() và uploadUserProfileImage() để thực hiện
     * việc tải lên file thực tế lên MinIO server.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     * <li>Tạo filename: "{prefix}{UUID}-{originalFilename}"</li>
     * <li>Lấy InputStream từ MultipartFile</li>
     * <li>Gọi MinIO putObject API để tải lên file</li>
     * <li>File được lưu với content type từ file upload</li>
     * <li>Trả về External URL: "{externalUrl}/{bucket}/{filename}"</li>
     * <li>Log error nếu upload thất bại, throw RuntimeException</li>
     * </ol>
     * 
     * <p><b>Tạo Filename:</b></p>
     * <ul>
     * <li>Format: "{prefix}{UUID}-{originalname}"</li>
     * <li>Ví dụ: "images/a1b2c3d4-e5f6-g7h8-product.jpg"</li>
     * <li>UUID được thêm để tránh trùng lặp khi upload cùng file nhiều lần</li>
     * </ul>
     * 
     * <p><b>URL Returned (External):</b></p>
     * <ul>
     * <li>Format: "{externalUrl}/{bucket}/{filename}"</li>
     * <li>Ví dụ: "http://localhost:9000/tenant-abc-assets/images/uuid-product.jpg"</li>
     * <li>URL này được trả về cho client để truy cập file qua internet</li>
     * <li>Khác với Internal URL mà MinioClient sử dụng nội bộ</li>
     * </ul>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     * <li>Phương thức private, chỉ được sử dụng nội bộ bởi upload methods</li>
     * <li>Sử dụng try-with-resources để đảm bảo stream được đóng</li>
     * <li>Content type được lấy từ file upload (ví dụ: image/jpeg)</li>
     * <li>File size được lấy từ MultipartFile</li>
     * <li>Nếu upload thất bại, error được log và RuntimeException được throw</li>
     * <li>MinioClient sử dụng Internal URL để giao tiếp với MinIO server</li>
     * <li>Phương thức trả về External URL để client sử dụng</li>
     * </ul>
     * 
     * <p><b>Luồng chi tiết:</b></p>
     * <pre>
     * {@code
     * 1. Backend upload file tới MinIO qua Internal URL (http://minio:9000)
     *    MinioClient -> http://minio:9000/bucket/file
     * 
     * 2. Backend trả về External URL cho client
     *    Response -> http://localhost:9000/bucket/file
     * 
     * 3. Client sử dụng External URL để download file
     *    Client -> http://localhost:9000/bucket/file
     * }
     * </pre>
     * 
     * @param bucket Tên bucket trên MinIO nơi file sẽ được lưu.
     *               Ví dụ: "tenant-abc-assets" hoặc "user-profiles"
     * @param file MultipartFile chứa file cần tải lên.
     *             Phải không null và không rỗng.
     * @param prefix Tiền tố thư mục trong bucket.
     *               Ví dụ: "images/" hoặc "avatars/"
     *               File sẽ được lưu tại: bucket/{prefix}{filename}
     * 
     * @return {@link String} External URL công cộng của file vừa tải lên.
     *         Định dạng: "http://localhost:9000/bucket/prefix/filename"
     *         URL này có thể được sử dụng bởi client để truy cập file.
     * 
     * @throws RuntimeException("Upload failed: ...")
     *         nếu xảy ra lỗi: kết nối MinIO, permission, disk space, v.v.
     * 
     * @see MinioClient#putObject(PutObjectArgs)
     */
    private String uploadFile(String bucket, MultipartFile file, String prefix) {
        String fileName = prefix + UUID.randomUUID() + "-" + file.getOriginalFilename();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(fileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            
            return String.format("%s/%s/%s", externalUrl, bucket, fileName);
            
        } catch (Exception e) {
            log.error("Upload failed", e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }

    /**
     * Xóa file khỏi MinIO dựa trên External URL công cộng của file.
     * 
     * <p>Phương thức này xóa file từ MinIO bằng cách phân tích External URL
     * (URL được gửi từ client) để trích xuất bucket name và object name.
     * Nếu xảy ra lỗi, phương thức chỉ log warning thay vì throw exception,
     * đảm bảo không làm gián đoạn flow của application (soft delete).</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     * <li>Kiểm tra URL có null hoặc rỗng, nếu có return sớm</li>
     * <li>Tách External URL để lấy bucket name và object name:</li>
     * <ul>
     * <li>Input URL: "http://localhost:9000/bucket-name/folder/file.jpg"</li>
     * <li>Loại bỏ "{externalUrl}/" từ đầu</li>
     * <li>Tách bằng '/' để được: ["bucket-name", "folder/file.jpg"]</li>
     * </ul>
     * <li>Kiểm tra nếu parts.length < 2 (URL format không hợp lệ), return sớm</li>
     * <li>Gọi MinIO removeObject() để xóa file</li>
     * <li>Log info nếu xóa thành công</li>
     * <li>Log warning nếu xảy ra lỗi (không throw)</li>
     * </ol>
     * 
     * <p><b>URL Parsing:</b></p>
     * <pre>
     * {@code
     * Input (External URL từ client):
     *   "http://localhost:9000/tenant-abc-assets/images/uuid-product.jpg"
     * 
     * Sau loại bỏ external URL prefix:
     *   "tenant-abc-assets/images/uuid-product.jpg"
     * 
     * Sau split:
     *   bucket = "tenant-abc-assets"
     *   objectName = "images/uuid-product.jpg"
     * }
     * </pre>
     * 
     * <p><b>Xử lý lỗi (Soft Delete):</b></p>
     * <ul>
     * <li>Nếu file không tồn tại, chỉ log warning, không throw exception</li>
     * <li>Nếu kết nối MinIO lỗi, chỉ log warning, không throw exception</li>
     * <li>Nếu permission bị từ chối, chỉ log warning, không throw exception</li>
     * <li>Điều này đảm bảo delete operation không làm gián đoạn application</li>
     * <li>Nên check logs để biết file có được xóa thành công không</li>
     * </ul>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     * <li>Phương thức này là "soft" - không throw exception nếu thất bại</li>
     * <li>Nên check logs để biết file có được xóa thành công không</li>
     * <li>Hữu ích khi xóa ảnh cũ sau khi user cập nhật ảnh mới</li>
     * <li>URL phải đúng format: "{externalUrl}/{bucket}/{objectName}"</li>
     * <li>Nếu URL null hoặc rỗng, phương thức sẽ return sớm (silent ignore)</li>
     * <li>MinioClient sử dụng Internal URL nội bộ để giao tiếp với MinIO server</li>
     * </ul>
     * 
     * <p><b>Ví dụ sử dụng:</b></p>
     * <pre>
     * {@code
     * String oldImageUrl = "http://localhost:9000/tenant-abc-assets/images/old-uuid-photo.jpg";
     * storageService.deleteFile(oldImageUrl);
     * // Nếu thành công: Log "🗑 Deleted file: ..."
     * // Nếu thất bại: Log "Failed to delete file: ..." (không throw error)
     * }
     * </pre>
     * 
     * @param fileUrl External URL công cộng của file cần xóa.
     *                Format: "http://localhost:9000/bucket-name/path/filename"
     *                URL này là URL được client gửi về (External URL, không phải Internal URL).
     *                Nếu null hoặc rỗng, phương thức sẽ return sớm.
     * 
     * @throws Không throw exception, chỉ log warning nếu xảy ra lỗi.
     *         Điều này là intentional để không làm gián đoạn application flow.
     *         Nên check logs để xác nhận file đã được xóa.
     * 
     * @see MinioClient#removeObject(RemoveObjectArgs)
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            String path = fileUrl.replace(externalUrl + "/", "");
            
            String[] parts = path.split("/", 2);
            if (parts.length < 2) return;

            String bucket = parts[0];
            String objectName = parts[1];

            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectName).build()
            );
            log.info("🗑 Deleted file: {}", fileUrl);
        } catch (Exception e) {
            log.warn("Failed to delete file: {}", fileUrl);
        }
    }
}