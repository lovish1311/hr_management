package com.example.hr_management_backend.features.tambola.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TambolaClaimResultDto {
    private boolean success;
    private String message;
    private String prizeType;
    private Long winnerEmployeeId;
    private String winnerEmployeeName;
    private int prizeRank;
    private Integer claimNumber;
    private LocalDateTime claimedAt;
}
