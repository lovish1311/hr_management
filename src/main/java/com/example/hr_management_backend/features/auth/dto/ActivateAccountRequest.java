package com.example.hr_management_backend.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivateAccountRequest {
    @NotBlank(message = "Activation key is required")
    private String activationKey;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;
}
