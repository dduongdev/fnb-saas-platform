package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.NotificationResponse;
import com.project.fnb.modules.pos.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller quản lý notifications.
 * 
 * <p>Controller này cung cấp các API cho chủ quán/nhân viên:</p>
 * <ul>
 *   <li>Lấy danh sách notifications</li>
 *   <li>Đánh dấu đã đọc</li>
 *   <li>Đếm notifications chưa đọc</li>
 * </ul>
 * 
 * <p><b>Authentication:</b> Yêu cầu JWT token (nhân viên đã đăng nhập).</p>
 * 
 * <p><b>Base URL:</b> {@code /api/notifications}</p>
 * 
 * @author FNB Team
 * @version 1.0
 * @see NotificationService
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Lấy danh sách notifications với phân trang.
     * 
     * @param page Số trang (mặc định 0)
     * @param size Số items mỗi trang (mặc định 20)
     * @return Page<NotificationResponse>
     */
    @GetMapping
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<Page<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(notificationService.getNotifications(pageable));
    }

    /**
     * Lấy notifications chưa đọc.
     * 
     * @return List<NotificationResponse>
     */
    @GetMapping("/unread")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<List<NotificationResponse>> getUnreadNotifications() {
        return ApiResponse.success(notificationService.getUnreadNotifications());
    }

    /**
     * Đếm số notifications chưa đọc.
     * 
     * @return Count
     */
    @GetMapping("/unread/count")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<Long> countUnread() {
        return ApiResponse.success(notificationService.countUnread());
    }

    /**
     * Lấy notifications gần đây (cho dropdown header).
     * 
     * @param limit Số lượng tối đa (mặc định 10)
     * @return List<NotificationResponse>
     */
    @GetMapping("/recent")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<List<NotificationResponse>> getRecentNotifications(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(notificationService.getRecentNotifications(limit));
    }

    /**
     * Đánh dấu notification đã đọc.
     * 
     * @param id ID của notification
     */
    @PostMapping("/{id}/read")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ApiResponse.success("Đã đánh dấu đã đọc");
    }

    /**
     * Đánh dấu tất cả notifications đã đọc.
     */
    @PostMapping("/read-all")
    @com.project.fnb.aspect.RequirePermission({com.project.fnb.aspect.OwnerPermissionValidator.class, com.project.fnb.aspect.WaiterPermissionValidator.class})
    public ApiResponse<String> markAllAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.success("Đã đánh dấu tất cả đã đọc");
    }
}
