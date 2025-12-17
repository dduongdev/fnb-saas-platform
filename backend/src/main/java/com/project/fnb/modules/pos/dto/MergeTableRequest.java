package com.project.fnb.modules.pos.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MergeTableRequest {
    @NotNull(message = "Phải chọn bàn đích")
    private Integer targetTableId;

    @NotEmpty(message = "Phải chọn ít nhất 1 bàn để gộp")
    private List<Integer> sourceTableIds;
}