package com.example.hr_management_backend.features.payroll.reconciliation;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollReconciliationReportDto {
    private String currentMonth;
    private Integer currentYear;
    private String previousMonth;
    private Integer previousYear;

    private Integer currentHeadcount;
    private Integer previousHeadcount;
    private Integer headcountDelta;

    private BigDecimal currentGrossTotal;
    private BigDecimal previousGrossTotal;
    private BigDecimal grossTotalDelta;
    private Double grossPercentageDelta;

    private BigDecimal currentNetTotal;
    private BigDecimal previousNetTotal;
    private BigDecimal netTotalDelta;
    private Double netPercentageDelta;

    private BigDecimal currentPfTotal;
    private BigDecimal currentEsiTotal;
    private BigDecimal currentPtTotal;
    private BigDecimal currentTdsTotal;
    private BigDecimal currentArrearsTotal;

    @Builder.Default
    private List<EmployeePayrollVarianceDto> employeeVariances = new ArrayList<>();
}
