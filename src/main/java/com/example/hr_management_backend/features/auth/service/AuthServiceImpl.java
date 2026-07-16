package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.features.auth.dto.LoginRequest;
import com.example.hr_management_backend.features.auth.dto.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        // Boilerplate login response
        return new LoginResponse("placeholder-jwt-token", loginRequest.getEmail(), "HR_ADMIN");
    }

    @Override
    public void logout(String token) {
        // Boilerplate logout logic
    }
}
