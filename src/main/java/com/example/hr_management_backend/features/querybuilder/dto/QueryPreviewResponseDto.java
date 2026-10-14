package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryPreviewResponseDto {
    private List<ColumnMetaDto> columns;
    private List<Map<String, Object>> rows;
    private Long totalMatchingRecords;
    private KpiSummaryDto kpiSummary;
    private Integer currentPage;
    private Integer pageSize;
    private Long executionTimeMs;
}
