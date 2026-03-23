package com.project.fnb.modules.global.dto;

import com.project.fnb.modules.global.entity.AccessRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessKeyDto {
    private String id;
    private String name;
    private String keyString;
    private AccessRole role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
