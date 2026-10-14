package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryTemplateDto {
    private Long id;
    private String name;
    private String description;
    private String category;
    private Boolean isPublic;
    private Long createdByEmployeeId;
    private QueryRequestDto queryConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
