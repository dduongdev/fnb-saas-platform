package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.MergeTableRequest;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.service.TableService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @GetMapping
    public ApiResponse<List<TableDto>> getTables() {
        return ApiResponse.success(tableService.getTables());
    }

    @PostMapping
    public ApiResponse<TableDto> createTable(@RequestParam String name) {
        return ApiResponse.success(tableService.createTable(name));
    }

    @PostMapping("/merge")
    public ApiResponse<String> mergeTables(@RequestBody @Valid MergeTableRequest request) {
        tableService.mergeTables(request);
        return ApiResponse.success("Đã gộp các bàn thành công");
    }

    @PostMapping("/{id}/split")
    public ApiResponse<String> splitTable(@PathVariable Integer id) {
        tableService.splitTable(id);
        return ApiResponse.success("Đã tách bàn / trả bàn về trạng thái trống");
    }
}