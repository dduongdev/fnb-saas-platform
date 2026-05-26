package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.service.KdsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller cho Kitchen Display System (KDS).
 * 
 * <p><b>Mục đích:</b> Cung cấp HTTP endpoints để tải dữ liệu KDS (sessions, items).
 * Được sử dụng bởi KDS frontend để tải dữ liệu ban đầu trước khi kết nối WebSocket.</p>
 * 
 * <p><b>API Endpoints:</b></p>
 * <ul>
 *   <li>GET /api/pos/kds/sessions - Lấy tất cả active sessions</li>
 * </ul>
 * 
 * <p><b>Use Case:</b></p>
 * <pre>
 * 1. KDS page mở
 * 2. Frontend gọi GET /api/pos/kds/sessions (HTTP) để tải dữ liệu ban đầu
 * 3. UI hiển thị sessions từ HTTP response
 * 4. Đồng thời, frontend kết nối WebSocket để nhận real-time updates
 * 5. Khi có changes, KdsEventPublisher gửi events qua WebSocket
 * </pre>
 * 
 * @author FNB Team
 * @version 1.0
 */
@RestController
@RequestMapping("/api/pos/kds")
@RequiredArgsConstructor
@Slf4j
public class KdsRestController {
    
    private final KdsService kdsService;
    
    /**
     * Lấy tất cả active sessions để hiển thị trên KDS.
     * 
     * <p><b>Request:</b></p>
     * GET /api/pos/kds/sessions
     * Headers:
     *   - Authorization: Bearer {token}  (required)
     *   - X-Tenant-ID: {tenantId}  (được extract bởi TenantFilter)
     * 
     * <p><b>Response (200):</b></p>
     * {
     *   "code": 200,
     *   "message": "Success",
     *   "data": [
     *     {
     *       "sessionId": "abc123",
     *       "tableId": "table1",
     *       "tableName": "Bàn 1",
     *       "status": "ACTIVE",
     *       "createdAt": "2026-01-01T10:00:00",
     *       "pendingItemCount": 3,
     *       "waitTime": "5 phút",
     *       "items": [
     *         { "itemId": "i1", "name": "Phở", "quantity": 2, "status": "PENDING", "createdAt": "..." },
     *         { "itemId": "i2", "name": "Cơm", "quantity": 1, "status": "PENDING", "createdAt": "..." }
     *       ]
     *     }
     *   ]
     * }
     * 
     * <p><b>Error Cases:</b></p>
     * - 401: Unauthorized (missing/invalid token)
     * - 403: Forbidden (no permission)
     * - 500: Server error
     * 
     * <p><b>Note:</b> Tenant context được set bởi TenantFilter từ X-Tenant-ID header.</p>
     * 
     * @return ApiResponse chứa List<KdsSessionDto> (active sessions)
     */
    @GetMapping("/sessions")
    @com.project.fnb.aspect.RequirePermission({
            com.project.fnb.aspect.OwnerPermissionValidator.class,
            com.project.fnb.aspect.KitchenPermissionValidator.class
    })
    public ApiResponse<List<KdsSessionDto>> getSessions() {
        try {
            log.debug("Fetching KDS sessions via REST API");
            List<KdsSessionDto> sessions = kdsService.getAllActiveSessions();
            log.debug("Found {} active sessions", sessions.size());
            return ApiResponse.success(sessions);
        } catch (Exception e) {
            log.error("Error fetching KDS sessions", e);
            throw new RuntimeException(e);
        }
    }
}
