package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationMessage {
    private String type; 
    private String title;
    private String content;
    private Integer tableId;
    private String tableName;
}