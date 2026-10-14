package com.example.hr_management_backend.features.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusResponse {
    private String email;
    private String status; // PENDING_ACTIVATION, ACTIVE, DEACTIVATED, NOT_FOUND
    private Boolean isActivated;
    private String firstName;
    private String message;
}
