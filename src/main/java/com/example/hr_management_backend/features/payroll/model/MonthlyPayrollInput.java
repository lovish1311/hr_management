package com.example.hr_management_backend.features.payroll.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_payroll_inputs", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payroll_input_emp_period", columnNames = {"employeeId", "payrollMonth", "payrollYear"})
}, indexes = {
    @Index(name = "idx_payroll_input_period", columnList = "payrollYear, payrollMonth")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyPayrollInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 20)
    private String payrollMonth; // e.g. "OCTOBER" or "10"

    @Column(nullable = false)
    private Integer payrollYear; // e.g. 2026

    @Column(nullable = false)
    @Builder.Default
    private Double lopDays = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Double overtimeHours = 0.0;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adHocBonus = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adHocDeduction = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal arrearsAmount = BigDecimal.ZERO;

    @Column(length = 20)
    @Builder.Default
    private String taxRegime = "NEW";

    @Column(precision = 12, scale = 2)
    private BigDecimal declared80C;

    @Column(precision = 12, scale = 2)
    private BigDecimal declared80D;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isLocked = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isExempt = false;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
