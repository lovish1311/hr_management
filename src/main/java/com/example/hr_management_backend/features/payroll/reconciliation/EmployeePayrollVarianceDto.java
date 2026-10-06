package com.example.hr_management_backend.features.payroll.reconciliation;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeePayrollVarianceDto {
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;

    private BigDecimal previousGrossPay;
    private BigDecimal currentGrossPay;
    private BigDecimal grossDifference;

    private BigDecimal previousNetPay;
    private BigDecimal currentNetPay;
    private BigDecimal netDifference;

    private Double previousLopDays;
    private Double currentLopDays;

    private BigDecimal arrearsAmount;
    private BigDecimal adHocBonus;
    private BigDecimal calculatedTds;

    private VarianceTag varianceTag;
    private String varianceReason;
}
