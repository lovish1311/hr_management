package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryRequestDto {
    @Builder.Default
    private String rootEntity = "EMPLOYEE"; // EMPLOYEE, ATTENDANCE, LEAVES, PAYROLL

    @Builder.Default
    private List<String> selectedFields = new ArrayList<>();

    @Builder.Default
    private Map<String, String> columnAliases = new HashMap<>();

    private FilterGroupDto filterGroup;

    private String sortBy;

    @Builder.Default
    private String sortDirection = "ASC"; // ASC, DESC

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer pageSize = 50;
}
