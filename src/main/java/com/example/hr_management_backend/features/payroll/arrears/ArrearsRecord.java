package com.example.hr_management_backend.features.payroll.arrears;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_arrears", indexes = {
    @Index(name = "idx_arrears_target", columnList = "targetYear, targetMonth, isProcessed"),
    @Index(name = "idx_arrears_emp", columnList = "employeeId, isProcessed")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArrearsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 20)
    private String targetMonth; // The payout month (e.g. "OCTOBER")

    @Column(nullable = false)
    private Integer targetYear; // e.g. 2026

    @Column(length = 20)
    private String sourceMonth; // The retrospective period it applies to (e.g. "AUGUST")

    private Integer sourceYear; // e.g. 2026

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal arrearsAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 255)
    private String reason; // e.g. "Retroactive annual salary revision effective August 1st"

    @Column(nullable = false)
    @Builder.Default
    private Boolean isProcessed = false;

    private UUID processedRecordId; // Linked PayrollRecord ID once disbursed

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
