package com.project.fnb.modules.global.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessKeyInfoDto {
    private String accessKeyId;
    private String tenantId;
    private String role;
}
