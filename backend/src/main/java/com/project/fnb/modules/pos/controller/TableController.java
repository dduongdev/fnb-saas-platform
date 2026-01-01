package com.project.fnb.modules.pos.controller;

import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.pos.dto.TableDto;
import com.project.fnb.modules.pos.service.TableService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TableController - CRUD bàn ăn.
 * Merge/Split/Transfer đã chuyển sang SessionController.
 */
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

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteTable(@PathVariable Integer id) {
        tableService.deleteTable(id);
        return ApiResponse.success("Đã xóa bàn");
    }
}