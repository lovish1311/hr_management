package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldMetadataDto {
    private String fieldId;
    private String label;
    private String dataType; // STRING, NUMBER, DATE, ENUM, BOOLEAN
    private boolean filterable;
    private boolean projectable;
    private List<String> allowedOperators;
    private List<String> options; // For ENUM data types
}
