package com.project.fnb.modules.global.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.global.dto.TenantPublicDto;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/tenants")
@RequiredArgsConstructor
public class PublicTenantController {

    private final TenantRepository tenantRepository;

    @GetMapping
    public ApiResponse<Page<TenantPublicDto>> getPublicTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        Page<Tenant> tenantPage = tenantRepository.findByIsActiveTrue(pageable);

        Page<TenantPublicDto> dtoPage = tenantPage.map(t -> TenantPublicDto.builder()
                .id(t.getId())
                .name(t.getName())
                .address(t.getAddress())
                .logoUrl(t.getLogoUrl())
                .build());

        return ApiResponse.success(dtoPage);
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantPublicDto> getPublicTenantDetail(@org.springframework.web.bind.annotation.PathVariable String id) {
        Tenant t = tenantRepository.findById(id)
                .orElseThrow(() -> new com.project.fnb.common.exception.AppException(404, "Quán không tồn tại"));
        TenantPublicDto dto = TenantPublicDto.builder()
                .id(t.getId())
                .name(t.getName())
                .address(t.getAddress())
                .logoUrl(t.getLogoUrl())
                .build();
        return ApiResponse.success(dto);
    }
}