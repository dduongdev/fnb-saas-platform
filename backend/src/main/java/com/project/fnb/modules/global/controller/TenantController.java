package com.project.fnb.modules.global.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.dto.CreateTenantRequest;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.dto.UpdateTenantRequest;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST API controller quản lý Tenant (Quán).
 * 
 * <p>Class này cung cấp các endpoint HTTP để quản lý thông tin tenant (quán hàng).
 * Bao gồm các thành viên thể tải lên logo, cập nhật thông tin, và truy vấn chi tiết.
 * Mỗi tenant được liên kết với owner (user) được xác định từ JWT token.</p>
 * 
 * <p><b>Base URL:</b> /api/tenants</p>
 * 
 * @author Project Team
 * @version 1.0
 * @see TenantService
 */
@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * Tạo mới tenant (quán hàng) với logo (optional).
     * 
     * <p>Endpoint này cho phép user đăng nhập tạo mới một tenant/quán hàng.
     * User sẽ đự lệnh làm owner (chủ nhân) của quán.
     * Đồng thời có thể upload logo quán lên cloud storage (optional).</p>
     * 
    * @param jwt JWT token, chứa user ID trong claim "sub"
     * @param name Tên quán hàng (bắt buộc)
     * @param address Địa chỉ quán hàng (bắt buộc)
     * @param logo File hình ảnh logo quán (optional, multipart)
     * 
     * @return ApiResponse<Tenant> chứa Tenant object đã được tạo
     * @see TenantService#createTenant(CreateTenantRequest, String, MultipartFile)
     */
    @PostMapping(consumes = { "multipart/form-data" })
    public ApiResponse<Tenant> createTenant(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String name,
            @RequestParam String address,
            @RequestParam(required = false) MultipartFile logo
    ) {
        String userId = jwt.getSubject();
        
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName(name);
        request.setAddress(address);
        
        return ApiResponse.success(tenantService.createTenant(request, userId, logo));
    }

    /**
     * Cập nhật thông tin tenant (quán hàng).
     * 
     * <p>Endpoint này cho phép owner cập nhật thông tin của tenant họ sở hữu.
     * Có thể cập nhật tên, địa chỉ, hoặc thay thế logo.
     * Các tham số không bắt buộc, chỉ cập nhật những giá trị được cung cấp (partial update).</p>
     * 
     * @param id ID của tenant cần cập nhật (path parameter, bắt buộc)
    * @param jwt JWT token dùng để authorization
     * @param name Tên quán hàng mới (optional)
     * @param address Địa chỉ quán hàng mới (optional)
     * @param logo File hình ảnh logo mới (optional)
     * 
     * @return ApiResponse<Tenant> chứa Tenant object đã được cập nhật
     * @throws RuntimeException("Tenant not found") nếu tenant không tồn tại
     * @see TenantService#updateTenant(String, UpdateTenantRequest, MultipartFile, String)
     */
    @PutMapping(value = "/{id}", consumes = { "multipart/form-data" })
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class})
    public ApiResponse<Tenant> updateTenant(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) MultipartFile logo
    ) {
        String userId = jwt.getSubject();
        
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setName(name);
        request.setAddress(address);

        return ApiResponse.success(tenantService.updateTenant(id, request, logo, userId));
    }

    /**
     * Lấy danh sách tất cả tenant của người dùng đăng nhập (owner).
     * 
     * <p>Endpoint này trả về tất cả tenant mà user hiện tại là owner (chủ nhân).
     * Danh sách được lấy từ JWT token để xác định user ID.</p>
     * 
    * @param jwt JWT token, chứa user ID trong claim "sub"
     * 
     * @return ApiResponse<List<Tenant>> danh sách tất cả tenant của user
     * @see TenantService#getMyTenants(String)
     */
    @GetMapping("/me")
    public ApiResponse<List<Tenant>> getMyTenants(@AuthenticationPrincipal Object principal) {
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getSubject();
            return ApiResponse.success(tenantService.getMyTenants(userId));
        } else if (principal instanceof com.project.fnb.infrastructure.security.AccessKeyUserDetails userDetails) {
            return ApiResponse.success(java.util.List.of(tenantService.getTenantDetail(userDetails.getTenantId())));
        }
        throw new com.project.fnb.common.exception.AppException(401, "Unsupported authentication type");
    }
    
    /**
     * Lấy chi tiết thông tin của một tenant cụ thể.
     * 
     * <p>Endpoint này lấy và trả về chi tiết đầy đủ của một tenant theo ID.
     * Endpoint này là PUBLIC (không yêu cầu JWT token), cho phép frontend
     * lấy thông tin chi tiết tenant để display truyền hình (banner, logo, v.v.)</p>
     * 
     * <p><b>Authorization:</b></p>
     * <ul>
     *   <li>Endpoint này là PUBLIC - không yêu cầu JWT token</li>
     *   <li>Bất kỳ ai cũng có thể lấy chi tiết tenant</li>
     * </ul>
     * 
     * @param id ID của tenant cần lấy chi tiết (path parameter, bắt buộc)
     * 
     * @return ApiResponse<Tenant> chứa chi tiết thông tin tenant
     * @throws RuntimeException("Tenant not found") nếu tenant không tồn tại
     * @see TenantService#getTenantDetail(String)
     */
    @GetMapping("/{id}")
    public ApiResponse<Tenant> getTenantDetail(@PathVariable String id) {
        return ApiResponse.success(tenantService.getTenantDetail(id));
    }

    @PatchMapping("/{id}/status")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class})
    public ApiResponse<String> updateStatus(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam Boolean isActive 
    ) {
        String userId = jwt.getSubject();
        tenantService.updateTenantStatus(id, isActive, userId);
        return ApiResponse.success("Cập nhật trạng thái thành công");
    }

    @PutMapping("/{id}/payment-config")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class})
    public ApiResponse<String> updatePaymentConfig(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PaymentConfigDto config
    ) {
        String userId = jwt.getSubject();
        tenantService.updatePaymentConfig(id, config, userId);
        return ApiResponse.success("Cập nhật cấu hình thanh toán thành công");
    }
}