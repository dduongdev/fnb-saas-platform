package com.project.fnb.modules.global.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantPublicDto {
    private String id;
    private String name;
    private String address;
    private String logoUrl;
}