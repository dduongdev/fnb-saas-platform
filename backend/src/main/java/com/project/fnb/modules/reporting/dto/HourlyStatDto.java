package com.project.fnb.modules.reporting.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HourlyStatDto {
    private Integer hour; 
    private Long orderCount;
}