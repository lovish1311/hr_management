package com.example.hr_management_backend.features.payroll.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSummaryDto {
    private String payrollMonth;
    private Integer payrollYear;
    private Integer totalHeadcount;
    private Integer processedCount;
    private Integer draftCount;
    private Integer verifiedCount;
    private Integer publishedCount;

    private BigDecimal totalGrossOutflow;
    private BigDecimal totalDeductionsOutflow;
    private BigDecimal totalNetPayout;

    // Variance vs previous month
    private BigDecimal priorMonthNetPayout;
    private Double variancePercentage;

    // Anomalies
    private List<String> anomalies;
}
