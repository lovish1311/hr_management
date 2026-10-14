package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterGroupDto {
    @Builder.Default
    private String conjunction = "AND"; // AND, OR

    @Builder.Default
    private List<FilterRuleDto> rules = new ArrayList<>();

    @Builder.Default
    private List<FilterGroupDto> subGroups = new ArrayList<>();
}
