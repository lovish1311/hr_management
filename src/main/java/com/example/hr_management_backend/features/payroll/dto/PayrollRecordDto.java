package com.example.hr_management_backend.features.payroll.dto;

import com.example.hr_management_backend.features.payroll.model.PayrollStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRecordDto {
    private UUID id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String designation;
    private String department;
    private String bankAccountNumber;

    private String payrollMonth;
    private Integer payrollYear;

    private Integer totalDaysInMonth;
    private Double paidDays;
    private Double lopDays;
    private Double overtimeHours;

    private BigDecimal masterFixedGross;
    private BigDecimal calculatedBasic;
    private BigDecimal calculatedHra;
    private BigDecimal calculatedConveyance;
    private BigDecimal calculatedMedical;
    private BigDecimal calculatedSpecial;
    private BigDecimal overtimeAmount;
    private BigDecimal adHocBonus;

    private BigDecimal calculatedPf;
    private BigDecimal calculatedEsi;
    private BigDecimal calculatedPt;
    private BigDecimal lopDeductionAmount;
    private BigDecimal adHocDeduction;

    private BigDecimal totalGrossPay;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;

    private PayrollStatus status;
    private LocalDateTime processedAt;
    private LocalDateTime publishedAt;
}
