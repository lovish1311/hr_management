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
public class FilterRuleDto {
    private String fieldId;
    private String operator; // EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH, GREATER_THAN, LESS_THAN, BETWEEN, BEFORE, AFTER, IN, IS_NULL, IS_NOT_NULL
    private List<String> values;
}
