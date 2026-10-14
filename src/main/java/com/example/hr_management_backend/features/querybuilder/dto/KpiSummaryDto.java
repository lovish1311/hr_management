package com.example.hr_management_backend.features.querybuilder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KpiSummaryDto {
    private Long totalRecords;
    private BigDecimal sumNetTakeHome;
    private BigDecimal sumGrossEarnings;
    private Double totalLeaves;
    private Double totalLopDays;
}
