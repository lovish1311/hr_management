package com.example.hr_management_backend.features.payroll.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_records", indexes = {
    @Index(name = "idx_payroll_record_emp_period", columnList = "employeeId, payrollYear, payrollMonth"),
    @Index(name = "idx_payroll_record_status", columnList = "status"),
    @Index(name = "idx_payroll_record_period", columnList = "payrollYear, payrollMonth")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long employeeId;

    private String employeeName;
    private String employeeCode;
    private String designation;
    private String department;
    private String bankAccountNumber;

    @Column(nullable = false, length = 20)
    private String payrollMonth; // e.g. "OCTOBER"

    @Column(nullable = false)
    private Integer payrollYear; // e.g. 2026

    // Calendar & Attendance snapshot
    private Integer totalDaysInMonth;
    private Double paidDays;
    private Double lopDays;
    private Double overtimeHours;

    // Fixed master base snapshot
    @Column(precision = 12, scale = 2)
    private BigDecimal masterFixedGross;

    // Calculated / Pro-rated earnings
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedBasic = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedHra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedConveyance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedMedical = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedSpecial = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adHocBonus = BigDecimal.ZERO;

    // Deductions breakdown
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedPf = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedEsi = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal calculatedPt = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lopDeductionAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adHocDeduction = BigDecimal.ZERO;

    // Financial totals
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalGrossPay = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal netPay = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    private LocalDateTime processedAt;
    private LocalDateTime publishedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
