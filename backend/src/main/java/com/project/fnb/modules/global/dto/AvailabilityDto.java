package com.project.fnb.modules.global.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalTime;

@Data
@Builder
public class AvailabilityDto {
    private Integer dayOfWeek; // 1 = CN, 2 = Thứ 2...
    private LocalTime startTime;
    private LocalTime endTime;
}