package com.project.fnb.modules.pos.dto;
import com.project.fnb.modules.pos.entity.DiningTable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableDto {
    private Integer id;
    private String name;
    private DiningTable.Status status;
    private String qrCodeUrl;
    private Long sessionId;
}