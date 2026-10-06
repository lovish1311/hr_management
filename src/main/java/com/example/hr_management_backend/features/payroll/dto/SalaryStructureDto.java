package com.example.hr_management_backend.features.payroll.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructureDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;
    private String designation;

    private BigDecimal basicPay;
    private BigDecimal hra;
    private BigDecimal conveyanceAllowance;
    private BigDecimal medicalAllowance;
    private BigDecimal specialAllowance;

    private BigDecimal pfContribution;
    private BigDecimal esiContribution;
    private BigDecimal professionalTax;
    private String taxRegime;
    private BigDecimal declared80C;
    private BigDecimal declared80D;
    private BigDecimal monthlyTdsOverride;

    private LocalDate effectiveDate;
    private Boolean isActive;

    private BigDecimal totalFixedGross;
    private BigDecimal totalStatutoryDeductions;
    private BigDecimal netCtc;
}
