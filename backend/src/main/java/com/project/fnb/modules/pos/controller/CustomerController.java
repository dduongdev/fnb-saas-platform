package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.menu.service.MenuService;
import com.project.fnb.modules.pos.dto.PublicMenuDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pos/public")
@RequiredArgsConstructor
public class CustomerController {

    private final MenuService menuService;
    private final TableRepository tableRepository;
    private final TenantRepository tenantRepository;

    @GetMapping("/menu")
    public ApiResponse<List<PublicMenuDto>> getMenu() {
        return ApiResponse.success(menuService.getPublicMenu());
    }

    @GetMapping("/info/{tableId}")
    public ApiResponse<Map<String, Object>> getTableInfo(@PathVariable Integer tableId) {
        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Bàn không tồn tại"));

        String tenantId = table.getTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        return ApiResponse.success(Map.of(
            "tableId", table.getId(),
            "tableName", table.getName(),
            "tenantName", tenant.getName(),
            "tenantLogo", tenant.getLogoUrl() != null ? tenant.getLogoUrl() : ""
        ));
    }
}