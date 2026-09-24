package com.example.hr_management_backend.features.tambola.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TambolaClaimRequestDto {

    @NotBlank(message = "Prize type is required")
    private String prizeType; // FIRST_HOUSE, SECOND_HOUSE, THIRD_HOUSE, FULL_HOUSE
}
