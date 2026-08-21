package com.example.hr_management_backend.features.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    @Builder.Default
    private String type = "Bearer";
    private Long id;
    private String email;
    private String role;
    private Long employeeId;

    public LoginResponse(String token, String email, String role) {
        this.token = token;
        this.type = "Bearer";
        this.email = email;
        this.role = role;
    }
}
