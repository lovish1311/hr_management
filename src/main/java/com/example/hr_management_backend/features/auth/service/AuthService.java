package com.example.hr_management_backend.features.auth.service;

import com.example.hr_management_backend.features.auth.dto.LoginRequest;
import com.example.hr_management_backend.features.auth.dto.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    void logout(String token);
}
