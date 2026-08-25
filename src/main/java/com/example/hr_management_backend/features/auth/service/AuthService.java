package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.features.auth.dto.LoginRequest;
import com.example.hr_management_backend.features.auth.dto.LoginResponse;
import com.example.hr_management_backend.features.auth.dto.RegisterRequest;
import com.example.hr_management_backend.features.auth.model.User;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    LoginResponse register(RegisterRequest registerRequest);
    User getCurrentUser(String email);
    User updateUserRole(Long userId, String newRole);
    void logout(String token);
}

