package com.project.fnb.modules.global.dto;

import com.project.fnb.modules.global.entity.AccessRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAccessKeyRequest {
    @NotBlank(message = "Tên quyền truy cập không được để trống")
    private String name;

    @NotNull(message = "Role không được để trống")
    private AccessRole role;
}
