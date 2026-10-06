package com.example.hr_management_backend.features.payroll.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyPayrollInputDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;
    private BigDecimal fixedGross;

    private String payrollMonth;
    private Integer payrollYear;
    private Double lopDays;
    private Double overtimeHours;
    private BigDecimal adHocBonus;
    private BigDecimal adHocDeduction;
    private BigDecimal arrearsAmount;
    private String taxRegime;
    private BigDecimal declared80C;
    private BigDecimal declared80D;
    private Boolean isLocked;
    private Boolean isExempt;
    private String notes;
}
