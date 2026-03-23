package com.project.fnb.modules.global.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class CreateTenantRequest {
    @NotBlank(message = "Tên quán không được để trống")
    private String name;
    
    private String address;
}