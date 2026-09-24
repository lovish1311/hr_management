package com.example.hr_management_backend.features.payroll.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "payroll", indexes = {
    @Index(name = "idx_payroll_emp_period", columnList = "employeeId, payPeriod")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payroll {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    private String employeeName;
    private String employeeCode;
    private String designation;
    private String department;

    // Earnings breakdown
    @Builder.Default
    private Double basicSalary = 0.0;

    @Builder.Default
    private Double hra = 0.0; // House Rent Allowance

    @Builder.Default
    private Double specialAllowance = 0.0;

    @Builder.Default
    private Double bonuses = 0.0;

    @Builder.Default
    private Double grossSalary = 0.0;

    // Deductions breakdown
    @Builder.Default
    private Double providentFund = 0.0; // PF

    @Builder.Default
    private Double professionalTax = 0.0;

    @Builder.Default
    private Double taxDeduction = 0.0; // TDS

    @Builder.Default
    private Double unpaidLeaveDeduction = 0.0;

    @Builder.Default
    private Double otherDeductions = 0.0;

    @Builder.Default
    private Double totalDeductions = 0.0;

    // Net pay & Period details
    @Builder.Default
    private Double netSalary = 0.0;

    @Column(nullable = false)
    private String payPeriod; // e.g. "2026-06", "Jun 2026"

    @Builder.Default
    private String paymentStatus = "PAID"; // PAID, PENDING, PROCESSING

    private LocalDate paymentDate;

    private Integer totalWorkingDays;
    private Integer daysWorked;
    private Integer unpaidDays;

    private String paymentMethod; // e.g. "Bank Transfer"
    private String bankAccountNumber; // Masked e.g. "•••• 4321"

    // Backward compatibility getters/setters if any legacy code refers to baseSalary or deductions
    public Double getBaseSalary() {
        return basicSalary;
    }

    public void setBaseSalary(Double baseSalary) {
        this.basicSalary = baseSalary != null ? baseSalary : 0.0;
    }

    public Double getDeductions() {
        return totalDeductions;
    }

    public void setDeductions(Double deductions) {
        this.totalDeductions = deductions != null ? deductions : 0.0;
    }
}
