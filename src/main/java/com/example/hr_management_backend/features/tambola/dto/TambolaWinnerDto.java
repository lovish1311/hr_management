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
public class TambolaWinnerDto {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String prizeType;
    private int prizeRank;
    private Integer claimNumber;
    private String winningDetails;
    private LocalDateTime claimedAt;
}
