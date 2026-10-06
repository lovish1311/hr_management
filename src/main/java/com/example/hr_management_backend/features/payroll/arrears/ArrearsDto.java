package com.example.hr_management_backend.features.payroll.arrears;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArrearsDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String targetMonth;
    private Integer targetYear;
    private String sourceMonth;
    private Integer sourceYear;
    private BigDecimal arrearsAmount;
    private String reason;
    private Boolean isProcessed;
    private UUID processedRecordId;
    private LocalDateTime createdAt;
}
