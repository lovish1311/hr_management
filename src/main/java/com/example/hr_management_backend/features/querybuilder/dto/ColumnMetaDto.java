package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnMetaDto {
    private String fieldId;
    private String label;
    private String dataType;
}
