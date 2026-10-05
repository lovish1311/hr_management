package com.example.hr_management_backend.features.payroll.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_structures", indexes = {
    @Index(name = "idx_sal_struct_emp_active", columnList = "employeeId, isActive")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basicPay = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal conveyanceAllowance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal medicalAllowance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal specialAllowance = BigDecimal.ZERO;

    // Statutory deductions
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal pfContribution = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal esiContribution = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal professionalTax = BigDecimal.ZERO;

    private LocalDate effectiveDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public BigDecimal getTotalFixedGross() {
        BigDecimal b = basicPay != null ? basicPay : BigDecimal.ZERO;
        BigDecimal h = hra != null ? hra : BigDecimal.ZERO;
        BigDecimal c = conveyanceAllowance != null ? conveyanceAllowance : BigDecimal.ZERO;
        BigDecimal m = medicalAllowance != null ? medicalAllowance : BigDecimal.ZERO;
        BigDecimal s = specialAllowance != null ? specialAllowance : BigDecimal.ZERO;
        return b.add(h).add(c).add(m).add(s);
    }

    public BigDecimal getTotalStatutoryDeductions() {
        BigDecimal pf = pfContribution != null ? pfContribution : BigDecimal.ZERO;
        BigDecimal esi = esiContribution != null ? esiContribution : BigDecimal.ZERO;
        BigDecimal pt = professionalTax != null ? professionalTax : BigDecimal.ZERO;
        return pf.add(esi).add(pt);
    }

    public BigDecimal getNetCtc() {
        return getTotalFixedGross().subtract(getTotalStatutoryDeductions());
    }
}
