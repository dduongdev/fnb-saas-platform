package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.service.TableService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller quản lý bàn ăn (Dining Tables).
 * 
 * <p>Controller này cung cấp các CRUD operations cơ bản cho bàn ăn:</p>
 * <ul>
 *   <li>Xem danh sách bàn</li>
 *   <li>Tạo bàn mới với QR code</li>
 *   <li>Xóa bàn (nếu không có session)</li>
 * </ul>
 * 
 * <p><b>Session Operations:</b> Các thao tác gộp bàn, tách bàn, chuyển bàn
 * đã được chuyển sang {@link SessionController} theo session-based model.</p>
 * 
 * <p><b>Authentication:</b> Yêu cầu JWT token (nhân viên đã đăng nhập).</p>
 * 
 * <p><b>Base URL:</b> {@code /api/pos/tables}</p>
 * 
 * @author FNB Team
 * @version 1.0
 * @see TableService
 * @see DiningTable
 * @see SessionController
 */
@RestController
@RequestMapping("/api/pos/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    /**
     * Lấy danh sách tất cả bàn của tenant.
     * 
     * <p>Trả về thông tin đầy đủ của mỗi bàn:</p>
     * <ul>
     *   <li>id, name, status</li>
     *   <li>sessionId (nếu bàn đang có khách)</li>
     *   <li>qrCodeUrl</li>
     * </ul>
     * 
     * <p><b>Use Case:</b> Hiển thị sơ đồ bàn trên POS dashboard.</p>
     * 
     * @return ApiResponse chứa List<TableDto>
     */
    @GetMapping
    public ApiResponse<List<TableDto>> getTables() {
        return ApiResponse.success(tableService.getTables());
    }

    /**
     * Tạo bàn mới.
     * 
     * <p>Tự động generate QR code cho bàn mới.
     * Bàn được tạo với trạng thái AVAILABLE.</p>
     * 
     * <p><b>QR Code:</b> Chứa URL tới trang customer menu với tableId.</p>
     * 
     * @param name tên bàn (VD: "Bàn 01", "Bàn VIP 1")
     * @return ApiResponse chứa TableDto của bàn vừa tạo
     */
    @PostMapping
    public ApiResponse<TableDto> createTable(@RequestParam String name) {
        return ApiResponse.success(tableService.createTable(name));
    }

    /**
     * Xóa bàn.
     * 
     * <p><b>Business Rule:</b> Chỉ xóa được bàn không có session.
     * Nếu bàn đang có khách (OCCUPIED) hoặc được đặt trước (RESERVED), không thể xóa.</p>
     * 
     * @param id ID của bàn cần xóa
     * @return ApiResponse với thông báo thành công
     * @throws AppException 400 nếu bàn đang có session
     * @throws AppException 404 nếu bàn không tồn tại
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTable(@PathVariable Integer id) {
        tableService.deleteTable(id);
        return ApiResponse.success("Đã xóa bàn");
    }
}