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
public class EntityMetadataDto {
    private String entityId;
    private String displayName;
    private String description;
    private List<FieldMetadataDto> fields;
}
